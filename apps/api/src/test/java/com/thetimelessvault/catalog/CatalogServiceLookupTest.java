package com.thetimelessvault.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceLookupTest {

    @Mock CatalogItemRepository catalogItems;
    @Mock BrickEconomyClient brickEconomy;

    CatalogService service;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new CatalogService(catalogItems, brickEconomy);
        lenient().when(catalogItems.save(any(CatalogItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void lookupWithoutSuffixUsesCanonicalSetAndFillsDatesAndPieces() throws Exception {
        when(catalogItems.findBySetNumberIgnoreCase("10237")).thenReturn(Optional.empty());
        when(catalogItems.findBySetNumberIgnoreCase("10237-1")).thenReturn(Optional.empty());
        when(brickEconomy.getSet("10237-1")).thenReturn(mapper.readTree("""
                {
                  "set_number": "10237-1",
                  "name": "Tower of Orthanc",
                  "theme": "The Lord of the Rings",
                  "subtheme": "The Two Towers",
                  "year": 2013,
                  "pieces_count": 2359,
                  "minifigs_count": 5,
                  "retired": true,
                  "released_date": "2013-07-01",
                  "retired_date": "2015-10-01"
                }
                """));

        CatalogItem item = service.lookup("10237", false);

        assertEquals("10237-1", item.getSetNumber());
        assertEquals("Tower of Orthanc", item.getName());
        assertEquals("The Two Towers", item.getSubtheme());
        assertEquals(2013, item.getYear());
        assertEquals(2359, item.getPiecesCount());
        assertEquals(5, item.getMinifigsCount());
        assertEquals(LocalDate.of(2013, 7, 1), item.getReleasedDate());
        assertEquals(LocalDate.of(2015, 10, 1), item.getRetiredDate());
        assertTrue(item.getRetired());
    }

    @Test
    void lookupReusesCanonicalCacheWhenBareNumberIsTyped() {
        CatalogItem cached = CatalogItem.create("10237-1");
        cached.setName("Tower of Orthanc");
        cached.setYear(2013);
        cached.setPiecesCount(2359);
        when(catalogItems.findBySetNumberIgnoreCase("10237-1")).thenReturn(Optional.of(cached));

        CatalogItem item = service.lookup("10237", false);

        assertEquals(cached, item);
        verify(brickEconomy, never()).getSet(any());
    }

    @Test
    void lookupRefreshesASparseStub() throws Exception {
        CatalogItem stub = CatalogItem.create("10237");
        stub.setName("10237");
        when(catalogItems.findBySetNumberIgnoreCase("10237")).thenReturn(Optional.of(stub));
        when(catalogItems.findBySetNumberIgnoreCase("10237-1")).thenReturn(Optional.empty());
        when(brickEconomy.getSet("10237-1")).thenReturn(mapper.readTree("""
                {
                  "set_number": "10237-1",
                  "name": "Tower of Orthanc",
                  "year": 2013,
                  "pieces": "2,359",
                  "released_date": "July 2013",
                  "retired_date": "2015-10-01T00:00:00Z"
                }
                """));

        CatalogItem item = service.lookup("10237", false);

        assertEquals("Tower of Orthanc", item.getName());
        assertEquals(2359, item.getPiecesCount());
        assertEquals(LocalDate.of(2013, 7, 1), item.getReleasedDate());
        assertEquals(LocalDate.of(2015, 10, 1), item.getRetiredDate());
        ArgumentCaptor<String> requested = ArgumentCaptor.forClass(String.class);
        verify(brickEconomy).getSet(requested.capture());
        assertEquals("10237-1", requested.getValue());
    }

    @Test
    void setNumberCandidatesAddsDefaultSuffix() {
        assertEquals(java.util.List.of("10237-1", "10237"), CatalogService.setNumberCandidates("10237"));
        assertEquals(java.util.List.of("10237-1", "10237"), CatalogService.setNumberCandidates("10237-1"));
        assertEquals(java.util.List.of("10123-1", "10123"), CatalogService.setNumberCandidates("10123"));
    }

    @Test
    void sparseDetectsRowsWithoutCatalogFacts() {
        CatalogItem namedStub = CatalogItem.create("10123");
        namedStub.setName("Cloud City");
        assertTrue(CatalogService.isSparse(namedStub));
        CatalogItem complete = CatalogItem.create("10123-1");
        complete.setName("Cloud City");
        complete.setYear(2003);
        complete.setPiecesCount(698);
        assertFalse(CatalogService.isSparse(complete));
    }

    @Test
    void lookupAcceptsSetsThatHaveYearAndPiecesButNoExactDates() throws Exception {
        when(catalogItems.findBySetNumberIgnoreCase("10123-1")).thenReturn(Optional.empty());
        when(catalogItems.findBySetNumberIgnoreCase("10123")).thenReturn(Optional.empty());
        when(brickEconomy.getSet("10123-1")).thenReturn(mapper.readTree("""
                {
                  "set_number": "10123-1",
                  "name": "Cloud City",
                  "theme": "Star Wars",
                  "subtheme": "Episode V",
                  "year": 2003,
                  "pieces_count": 698,
                  "minifigs_count": 7,
                  "retired": true
                }
                """));

        CatalogItem item = service.lookup("10123", false);

        assertEquals("10123-1", item.getSetNumber());
        assertEquals("Cloud City", item.getName());
        assertEquals(2003, item.getYear());
        assertEquals(698, item.getPiecesCount());
        assertTrue(item.getRetired());
        assertEquals(null, item.getReleasedDate());
        assertEquals(null, item.getRetiredDate());
    }
}
