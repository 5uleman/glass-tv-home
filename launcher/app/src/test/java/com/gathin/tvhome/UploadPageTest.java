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
    public void sizeCapIsTwentyKilobytes() {
        assertEquals(20_480, UploadPage.MAX_BYTES);
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
        String open = "<a href=\"" + UploadPage.GITHUB_URL + "\""
                + " target=\"_blank\" rel=\"noopener noreferrer\">";
        int at = UploadPage.HTML.indexOf(open);
        assertTrue("safe footer link missing", at >= 0);
        int end = UploadPage.HTML.indexOf("</a>", at);
        assertTrue("footer link not closed", end > at);
        String inner = UploadPage.HTML.substring(at + open.length(), end);
        assertTrue(inner, inner.startsWith("5ULEMAN"));
        assertTrue("external-link icon missing", inner.contains("<svg"));
    }

    @Test
    public void footerLinkIsAlwaysUnderlinedInAccentBlue() {
        Matcher m = Pattern.compile("\\.foot a\\{([^}]*)\\}").matcher(UploadPage.HTML);
        assertTrue("footer link rule missing", m.find());
        String rule = m.group(1);
        assertTrue(rule, rule.contains("text-decoration:underline"));
        assertTrue(rule, rule.contains("color:#3B82F6"));
        assertTrue("tap target must be 44 px", rule.contains("min-height:44px"));
        assertFalse("underline must not depend on hover", UploadPage.HTML.contains(".foot a:hover"));
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
    public void listRowsShowPhotoDateAndSize() {
        assertTrue(UploadPage.HTML.contains("'Photo · '+it.date"));
        assertTrue(UploadPage.HTML.contains("E('span','rsub',it.size)"));
    }

    @Test
    public void lockAndDeleteAreIconOnlyButtons() {
        assertTrue(UploadPage.HTML.contains("'act lock'"));
        assertTrue(UploadPage.HTML.contains("'act del'"));
        assertTrue(UploadPage.HTML.contains("setAttribute('aria-pressed'"));
        assertTrue(UploadPage.HTML.contains("setAttribute('aria-label'"));
        assertTrue(UploadPage.HTML.contains("id=\"iLock\""));
        assertTrue(UploadPage.HTML.contains("id=\"iTrash\""));
    }

    @Test
    public void lockedListOffersTheCycleAction() {
        assertTrue(UploadPage.HTML.contains("'Locked on 1 photo'"));
        assertTrue(UploadPage.HTML.contains("'Cycling through '+items.length"));
        assertTrue(UploadPage.HTML.contains("Cycle all photos"));
        assertTrue(UploadPage.HTML.contains("/api/slideshow"));
    }

    @Test
    public void selectModeHasCountConfirmationAndDone() {
        assertTrue(UploadPage.HTML.contains("Select all"));
        assertTrue(UploadPage.HTML.contains("'Delete ('"));
        assertTrue(UploadPage.HTML.contains("id=\"selbar\""));
        assertTrue(UploadPage.HTML.contains("id=\"done\""));
        assertTrue(UploadPage.HTML.contains("Delete this photo?"));
        assertTrue(UploadPage.HTML.contains("'/api/delete?file='"));
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
