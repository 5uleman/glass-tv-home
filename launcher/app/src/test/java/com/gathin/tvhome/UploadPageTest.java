package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

public class UploadPageTest {

    @Test
    public void fitsTheStatedSizeCap() {
        int bytes = UploadPage.HTML.getBytes(StandardCharsets.UTF_8).length;
        assertTrue("page is " + bytes + " bytes, cap is " + UploadPage.MAX_BYTES,
                bytes < UploadPage.MAX_BYTES);
    }

    @Test
    public void onlyExternalUrlIsTheFooterGitHubLink() {
        Matcher m = Pattern.compile("(?:https?:)?//[^\\s\"'<>]+").matcher(UploadPage.HTML);
        boolean seen = false;
        while (m.find()) {
            assertEquals(UploadPage.GITHUB_URL, m.group());
            seen = true;
        }
        assertTrue("footer link missing", seen);
    }

    @Test
    public void footerLinkIsTheRealAccountAndOpensSafely() {
        assertEquals("https://github.com/5uleman", UploadPage.GITHUB_URL);
        String link = "<a href=\"" + UploadPage.GITHUB_URL + "\""
                + " target=\"_blank\" rel=\"noopener noreferrer\">5ULEMAN</a>";
        assertTrue("safe footer link missing", UploadPage.HTML.contains(link));
    }

    @Test
    public void keepsEveryEndpointTheServerServes() {
        String[] endpoints = {
                "/upload?name=", "/api/list", "/api/select?file=",
                "/api/delete?file=", "/api/slideshow", "/thumb?file=",
        };
        for (String endpoint : endpoints) {
            assertTrue(endpoint, UploadPage.HTML.contains(endpoint));
        }
    }

    @Test
    public void namesAreWrittenAsTextNotMarkup() {
        assertFalse(UploadPage.HTML.contains("innerHTML"));
        assertFalse(UploadPage.HTML.contains("outerHTML"));
        assertFalse(UploadPage.HTML.contains("document.write"));
    }

    @Test
    public void pullsNothingFromOutsideTheTv() {
        assertFalse(UploadPage.HTML.contains("<link"));
        assertFalse(UploadPage.HTML.contains("@import"));
        assertFalse(UploadPage.HTML.contains("url("));
        assertFalse(UploadPage.HTML.contains("fonts.googleapis"));
        assertFalse(UploadPage.HTML.contains("cdn.tailwindcss"));
        assertFalse(UploadPage.HTML.contains("src=\"http"));
    }
}
