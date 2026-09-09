#!/usr/bin/env python3
"""Delete unused market_listing rows in small batches.

Keeps listings that belong to:
  - the latest snapshot per catalog + platform + condition
  - any snapshot scanned within the retention window (default 7 days)

Everything else is leftover scan history and can go. Run the script many
times; each invocation deletes at most --loops batches of --batch-size rows.
"""

from __future__ import annotations

import argparse
import os
import sys
import time


def connect_cloud_sql(instance: str, database: str, user: str, password: str):
    from google.cloud.sql.connector import Connector

    connector = Connector()
    conn = connector.connect(
        instance,
        "pg8000",
        user=user,
        password=password,
        db=database,
    )
    conn.autocommit = True
    return connector, conn


def connect_local(url: str, user: str, password: str):
    import pg8000.dbapi

    host, port, database = "localhost", 5432, "timeless_vault"
    if url.startswith("jdbc:postgresql://"):
        rest = url[len("jdbc:postgresql://") :]
        hostport, _, database = rest.partition("/")
        database = database.split("?")[0] or database
        if ":" in hostport:
            host, port_s = hostport.rsplit(":", 1)
            port = int(port_s)
        else:
            host = hostport
    conn = pg8000.dbapi.connect(user=user, password=password, host=host, port=port, database=database)
    conn.autocommit = True
    return None, conn


def execute(conn, sql, args=()):
    cur = conn.cursor()
    cur.execute(sql, args)
    return cur


def prepare_stale(conn, retention_days: int) -> int:
    execute(conn, "DROP TABLE IF EXISTS stale_snapshot")
    execute(
        conn,
        """
        CREATE TEMP TABLE stale_snapshot (
            id UUID PRIMARY KEY
        )
        """,
    )
    execute(
        conn,
        """
        INSERT INTO stale_snapshot (id)
        SELECT s.id
        FROM market_snapshot s
        WHERE s.scanned_at < now() - make_interval(days => %s)
          AND NOT EXISTS (
              SELECT 1
              FROM (
                  SELECT DISTINCT ON (catalog_item_id, platform, condition) id
                  FROM market_snapshot
                  ORDER BY catalog_item_id, platform, condition, scanned_at DESC
              ) latest
              WHERE latest.id = s.id
          )
        """,
        (retention_days,),
    )
    cur = execute(conn, "SELECT COUNT(*) FROM stale_snapshot")
    return cur.fetchone()[0]


def count_unused(conn) -> int:
    cur = execute(
        conn,
        """
        SELECT COUNT(*)
        FROM market_listing ml
        JOIN stale_snapshot st ON st.id = ml.snapshot_id
        """,
    )
    return cur.fetchone()[0]


def delete_batch(conn, batch_size: int) -> int:
    cur = execute(
        conn,
        """
        DELETE FROM market_listing
        WHERE id IN (
            SELECT ml.id
            FROM market_listing ml
            JOIN stale_snapshot st ON st.id = ml.snapshot_id
            LIMIT %s
        )
        """,
        (batch_size,),
    )
    return cur.rowcount or 0


def delete_empty_snapshots(conn, batch_size: int) -> int:
    cur = execute(
        conn,
        """
        DELETE FROM market_snapshot
        WHERE id IN (
            SELECT s.id
            FROM market_snapshot s
            JOIN stale_snapshot st ON st.id = s.id
            WHERE NOT EXISTS (
                SELECT 1 FROM market_listing ml WHERE ml.snapshot_id = s.id
            )
            LIMIT %s
        )
        """,
        (batch_size,),
    )
    return cur.rowcount or 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--batch-size", type=int, default=5000, help="Listings to delete per batch")
    parser.add_argument("--loops", type=int, default=1, help="How many listing batches to run")
    parser.add_argument("--until-done", action="store_true", help="Keep deleting until no unused rows remain")
    parser.add_argument("--retention-days", type=int, default=7)
    parser.add_argument("--purge-empty-snapshots", action="store_true", help="Also delete leftover empty snapshots")
    parser.add_argument("--dry-run", action="store_true", help="Count only; do not delete")
    parser.add_argument("--prod", action="store_true", help="Use Cloud SQL in PROJECT_ID")
    parser.add_argument(
        "--instance",
        default=os.environ.get("CLOUDSQL_INSTANCE", "the-timeless-vault:us-east1:timeless-vault"),
    )
    parser.add_argument("--database", default=os.environ.get("POSTGRES_DB", "timeless_vault"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    user = os.environ.get("POSTGRES_USER", "vault")
    password = os.environ.get("POSTGRES_PASSWORD", "vault")
    if args.prod:
        connector, conn = connect_cloud_sql(args.instance, args.database, user, password)
    else:
        connector, conn = connect_local(
            os.environ.get("DATABASE_URL", "jdbc:postgresql://localhost:5432/timeless_vault"),
            user,
            password,
        )
    try:
        started = time.time()
        print(f"Building stale snapshot set (retention {args.retention_days}d)...", flush=True)
        stale = prepare_stale(conn, args.retention_days)
        unused = count_unused(conn)
        print(f"stale_snapshots={stale} unused_listings={unused}", flush=True)
        if args.dry_run:
            return 0

        loops = 10_000 if args.until_done else max(1, args.loops)
        deleted = 0
        for i in range(loops):
            batch = delete_batch(conn, args.batch_size)
            deleted += batch
            print(
                f"batch {i + 1}: deleted {batch} listings (total {deleted}, {time.time() - started:.0f}s)",
                flush=True,
            )
            if batch < args.batch_size:
                break

        if args.purge_empty_snapshots:
            snaps = 0
            while True:
                batch = delete_empty_snapshots(conn, args.batch_size)
                snaps += batch
                print(f"empty snapshots deleted {batch} (total {snaps})", flush=True)
                if batch < args.batch_size:
                    break

        leftover = count_unused(conn)
        print(f"done deleted_listings={deleted} leftover_unused_listings={leftover}", flush=True)
        return 0
    finally:
        conn.close()
        if connector is not None:
            connector.close()


if __name__ == "__main__":
    sys.exit(main())
