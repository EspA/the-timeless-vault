package com.thetimelessvault.ebay;

import com.thetimelessvault.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EbayPictureHostingTest {

    @Test
    void acceptsPublicHttpsImagesOnly() {
        assertTrue(EbayClient.isPublicHttpsImageUrl("https://storage.googleapis.com/bucket/photo.jpg"));
        assertFalse(EbayClient.isPublicHttpsImageUrl("http://localhost:8080/api/photos/file/a.png"));
        assertFalse(EbayClient.isPublicHttpsImageUrl("https://localhost:8080/api/photos/file/a.png"));
        assertFalse(EbayClient.isPublicHttpsImageUrl("https://127.0.0.1/photo.png"));
        assertFalse(EbayClient.isPublicHttpsImageUrl(null));
    }

    @Test
    void readsPictureUrlFromTradingResponse() {
        String xml = """
                <UploadSiteHostedPicturesResponse>
                  <Ack>Success</Ack>
                  <SiteHostedPictureDetails>
                    <FullURL>https://i.ebayimg.com/00/s/photo.jpg</FullURL>
                  </SiteHostedPictureDetails>
                </UploadSiteHostedPicturesResponse>
                """;
        assertEquals("https://i.ebayimg.com/00/s/photo.jpg", EbayClient.pictureUrlFromTradingXml(xml));
    }

    @Test
    void surfacesTradingUploadFailure() {
        String xml = """
                <UploadSiteHostedPicturesResponse>
                  <Ack>Failure</Ack>
                  <Errors><LongMessage>Picture is too small</LongMessage></Errors>
                </UploadSiteHostedPicturesResponse>
                """;
        ApiException error = assertThrows(ApiException.class, () -> EbayClient.pictureUrlFromTradingXml(xml));
        assertTrue(error.getMessage().contains("Picture is too small"));
    }

    @Test
    void upscalesSmallPicturesToEbayMinimum() throws Exception {
        java.awt.image.BufferedImage source = new java.awt.image.BufferedImage(394, 292, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream original = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(source, "png", original);

        EbayPictures.Prepared prepared = EbayPictures.ensureMinimum(original.toByteArray(), "image/png");

        assertTrue(prepared.upscaled());
        assertEquals("image/jpeg", prepared.contentType());
        java.awt.image.BufferedImage result = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(prepared.bytes()));
        assertEquals(500, Math.max(result.getWidth(), result.getHeight()));
    }

    @Test
    void keepsPicturesThatAlreadyMeetEbayMinimum() throws Exception {
        java.awt.image.BufferedImage source = new java.awt.image.BufferedImage(800, 600, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream original = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(source, "jpg", original);
        byte[] bytes = original.toByteArray();

        EbayPictures.Prepared prepared = EbayPictures.ensureMinimum(bytes, "image/jpeg");

        assertFalse(prepared.upscaled());
        assertEquals(bytes, prepared.bytes());
    }
}
