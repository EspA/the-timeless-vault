package com.thetimelessvault.inventory;

import com.thetimelessvault.storage.ObjectStorage;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class InventoryController {

    private final InventoryService inventoryService;
    private final PhotoRepository photos;
    private final ObjectStorage storage;

    public InventoryController(InventoryService inventoryService, PhotoRepository photos, ObjectStorage storage) {
        this.inventoryService = inventoryService;
        this.photos = photos;
        this.storage = storage;
    }

    @GetMapping("/inventory")
    public List<InventoryDtos.InventoryView> list() {
        return inventoryService.list().stream().map(inventoryService::toView).toList();
    }

    @PostMapping("/inventory")
    public InventoryDtos.InventoryView create(@Valid @RequestBody InventoryDtos.CreateRequest request) {
        return inventoryService.toView(inventoryService.create(request));
    }

    @GetMapping("/inventory/{id}")
    public InventoryDtos.InventoryView get(@PathVariable UUID id) {
        return inventoryService.toView(inventoryService.get(id));
    }

    @PutMapping("/inventory/{id}")
    public InventoryDtos.InventoryView update(@PathVariable UUID id, @RequestBody InventoryDtos.UpdateRequest request) {
        return inventoryService.toView(inventoryService.update(id, request));
    }

    @DeleteMapping("/inventory/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        inventoryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/inventory/{id}/photos")
    public InventoryDtos.PhotoView upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        Photo photo = inventoryService.addPhoto(id, file);
        return new InventoryDtos.PhotoView(
                photo.getId(),
                storage.publicUrl(photo.getStorageKey()),
                photo.getOriginalFilename(),
                photo.getSortOrder(),
                photo.isPrimaryForBricklink()
        );
    }

    @DeleteMapping("/inventory/{id}/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        inventoryService.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/inventory/{id}/photos/{photoId}/primary")
    public void markPrimary(@PathVariable UUID id, @PathVariable UUID photoId) {
        inventoryService.markPrimary(id, photoId);
    }

    @GetMapping("/photos/file/{*key}")
    public ResponseEntity<InputStreamResource> file(@PathVariable("key") String key) {
        Photo photo = photos.findAll().stream()
                .filter(p -> p.getStorageKey().equals(key) || ("/" + p.getStorageKey()).equals(key))
                .findFirst()
                .orElse(null);
        String contentType = photo == null || photo.getContentType() == null ? MediaType.IMAGE_JPEG_VALUE : photo.getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(storage.read(key.startsWith("/") ? key.substring(1) : key)));
    }
}
