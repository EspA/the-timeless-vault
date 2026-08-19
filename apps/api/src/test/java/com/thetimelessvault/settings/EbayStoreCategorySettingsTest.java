package com.thetimelessvault.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thetimelessvault.common.ApiException;
import com.thetimelessvault.common.ThemeMapper;
import com.thetimelessvault.identity.AppSetting;
import com.thetimelessvault.identity.AppSettingRepository;
import com.thetimelessvault.inventory.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EbayStoreCategorySettingsTest {

    @Mock AppSettingRepository settings;
    @Mock InventoryItemRepository items;

    EbayStoreCategorySettings categories;

    @BeforeEach
    void setUp() {
        categories = new EbayStoreCategorySettings(settings, items, new ObjectMapper());
    }

    @Test
    void usesBuiltInListWhenSettingIsMissing() {
        when(settings.findById(EbayStoreCategorySettings.KEY)).thenReturn(Optional.empty());
        assertEquals(ThemeMapper.EBAY_STORE_CATEGORIES, categories.list());
        assertEquals("Star Wars", categories.entries().get(7).get("path"));
    }

    @Test
    void addPersistsBuiltInListPlusNewName() throws Exception {
        when(settings.findById(EbayStoreCategorySettings.KEY)).thenReturn(Optional.empty());

        List<String> saved = categories.add("Speed Champions");

        assertEquals("Speed Champions", saved.getLast());
        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        verify(settings).save(captor.capture());
        assertEquals(EbayStoreCategorySettings.KEY, captor.getValue().getKey());
        assertEquals(new ObjectMapper().writeValueAsString(saved), captor.getValue().getValue());
    }

    @Test
    void addRejectsDuplicateIgnoringCase() {
        when(settings.findById(EbayStoreCategorySettings.KEY))
                .thenReturn(Optional.of(new AppSetting(EbayStoreCategorySettings.KEY, "[\"Star Wars\"]")));
        assertThrows(ApiException.class, () -> categories.add("star wars"));
    }

    @Test
    void deleteClearsMatchingItemsAndNestedPaths() {
        when(settings.findById(EbayStoreCategorySettings.KEY))
                .thenReturn(Optional.of(new AppSetting(EbayStoreCategorySettings.KEY, "[\"Star Wars\",\"Marvel\"]")));

        List<String> remaining = categories.delete("star wars");

        assertEquals(List.of("Marvel"), remaining);
        verify(items).clearEbayStoreCategory("Star Wars");
        ArgumentCaptor<AppSetting> captor = ArgumentCaptor.forClass(AppSetting.class);
        verify(settings).save(captor.capture());
        assertEquals("[\"Marvel\"]", captor.getValue().getValue());
    }

    @Test
    void deleteUnknownCategoryFails() {
        when(settings.findById(EbayStoreCategorySettings.KEY))
                .thenReturn(Optional.of(new AppSetting(EbayStoreCategorySettings.KEY, "[\"Marvel\"]")));
        assertThrows(ApiException.class, () -> categories.delete("Star Wars"));
    }
}
