package com.thetimelessvault.ebay;

import com.thetimelessvault.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayActiveListingsTest {

    @Test
    void readsActiveListingsAndPaginates() {
        String xml = """
                <GetMyeBaySellingResponse>
                  <Ack>Success</Ack>
                  <ActiveList>
                    <ItemArray>
                      <Item>
                        <ItemID>123456789012</ItemID>
                        <Title><![CDATA[LEGO Star Wars 75192 UCS Millennium Falcon]]></Title>
                        <SKU>TTV-75192-1-AB12</SKU>
                        <QuantityAvailable>2</QuantityAvailable>
                        <SellingStatus>
                          <CurrentPrice currencyID="USD">849.99</CurrentPrice>
                        </SellingStatus>
                        <PictureDetails>
                          <GalleryURL>https://i.ebayimg.com/00/s/gallery.jpg</GalleryURL>
                          <PictureURL>https://i.ebayimg.com/00/s/photo.jpg</PictureURL>
                        </PictureDetails>
                        <ConditionID>1000</ConditionID>
                        <ConditionDisplayName>New</ConditionDisplayName>
                      </Item>
                    </ItemArray>
                    <PaginationResult>
                      <TotalNumberOfPages>3</TotalNumberOfPages>
                    </PaginationResult>
                  </ActiveList>
                </GetMyeBaySellingResponse>
                """;

        EbayActiveListings.Page page = EbayActiveListings.parse(xml);

        assertEquals(3, page.totalPages());
        assertEquals(1, page.listings().size());
        EbayActiveListing listing = page.listings().getFirst();
        assertEquals("123456789012", listing.listingId());
        assertEquals("TTV-75192-1-AB12", listing.sku());
        assertEquals("LEGO Star Wars 75192 UCS Millennium Falcon", listing.title());
        assertEquals(2, listing.quantity());
        assertEquals(new BigDecimal("849.99"), listing.price());
        assertEquals(2, listing.imageUrls().size());
        assertEquals("1000", listing.conditionId());
    }

    @Test
    void surfacesTradingFailure() {
        String xml = """
                <GetMyeBaySellingResponse>
                  <Ack>Failure</Ack>
                  <Errors><LongMessage>Auth token is invalid</LongMessage></Errors>
                </GetMyeBaySellingResponse>
                """;
        ApiException error = assertThrows(ApiException.class, () -> EbayActiveListings.parse(xml));
        assertTrue(error.getMessage().contains("Auth token is invalid"));
    }
}
