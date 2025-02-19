// Created by Fynn Godau 2019, licensed GNU GPL version 3 or later

package org.schabi.newpipe.extractor.services.bandcamp.extractors;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import org.jsoup.Jsoup;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.localization.DateWrapper;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class BandcampExtractorHelper {

    public static final String BASE_URL = "https://bandcamp.com";
    public static final String BASE_API_URL = BASE_URL + "/api";

    /**
     * Translate all these parameters together to the URL of the corresponding album or track
     * using the mobile API
     */
    public static String getStreamUrlFromIds(final long bandId, final long itemId, final String itemType)
            throws ParsingException {

        try {
            final String jsonString = NewPipe.getDownloader().get(
                    BASE_API_URL + "/mobile/22/tralbum_details?band_id=" + bandId
                            + "&tralbum_id=" + itemId + "&tralbum_type=" + itemType.charAt(0))
                    .responseBody();

            return JsonParser.object().from(jsonString)
                    .getString("bandcamp_url").replace("http://", "https://");

        } catch (final JsonParserException | ReCaptchaException | IOException e) {
            throw new ParsingException("Ids could not be translated to URL", e);
        }

    }

    /**
     * Fetch artist details from mobile endpoint.
     * <a href=https://notabug.org/fynngodau/bandcampDirect/wiki/rewindBandcamp+%E2%80%93+Fetching+artist+details>
     * More technical info.</a>
     */
    public static JsonObject getArtistDetails(String id) throws ParsingException {
        try {
            return
                    JsonParser.object().from(
                            NewPipe.getDownloader().post(
                                    BASE_API_URL + "/mobile/22/band_details",
                                    null,
                                    JsonWriter.string()
                                            .object()
                                            .value("band_id", id)
                                            .end()
                                            .done()
                                            .getBytes()
                            ).responseBody()
                    );
        } catch (final IOException | ReCaptchaException | JsonParserException e) {
            throw new ParsingException("Could not download band details", e);
        }
    }

    /**
     * @param id    The image ID
     * @param album Whether this is the cover of an album
     * @return URL of image with this ID in size 10 which is 1200x1200 (we could also choose size 0
     * but we don't want something as large as 3460x3460 here)
     */
    public static String getImageUrl(final long id, final boolean album) {
        return "https://f4.bcbits.com/img/" + (album ? 'a' : "") + id + "_10.jpg";
    }

    /**
     * @return <code>true</code> if the given URL looks like it comes from a bandcamp custom domain
     * or if it comes from <code>bandcamp.com</code> itself
     */
    public static boolean isSupportedDomain(final String url) throws ParsingException {

        // Accept all bandcamp.com URLs
        if (url.toLowerCase().matches("https?://.+\\.bandcamp\\.com(/.*)?")) return true;

        try {
            // Test other URLs for whether they contain a footer that links to bandcamp
            return Jsoup.parse(
                    NewPipe.getDownloader().get(url).responseBody()
            )
                    .getElementById("pgFt")
                    .getElementById("pgFt-inner")
                    .getElementById("footer-logo-wrapper")
                    .getElementById("footer-logo")
                    .getElementsByClass("hiddenAccess")
                    .text().equals("Bandcamp");
        } catch (NullPointerException e) {
            return false;
        } catch (IOException | ReCaptchaException e) {
            throw new ParsingException("Could not determine whether URL is custom domain " +
                    "(not available? network error?)");
        }
    }

    /**
     * Whether the URL points to a radio kiosk.
     * @param url the URL to check
     * @return true if the URL matches <code>https://bandcamp.com/?show=SHOW_ID</code>
     */
    public static boolean isRadioUrl(final String url) {
        return url.toLowerCase().matches("https?://bandcamp\\.com/\\?show=\\d+");
    }

    static DateWrapper parseDate(final String textDate) throws ParsingException {
        try {
            final ZonedDateTime zonedDateTime = ZonedDateTime.parse(
                    textDate, DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH));
            return new DateWrapper(zonedDateTime.toOffsetDateTime(), false);
        } catch (final DateTimeException e) {
            throw new ParsingException("Could not parse date '" + textDate + "'", e);
        }
    }
}
