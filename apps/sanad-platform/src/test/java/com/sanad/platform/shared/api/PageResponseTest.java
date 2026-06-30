package com.sanad.platform.shared.api;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {

    @Test
    void buildsPageResponseWithMetadata() {
        var response = PageResponse.of(
            List.of("item1", "item2"), 0, 20, 2, 1,
            true, true, false, false,
            List.of(new SortMetadata("name", "asc"))
        );
        assertEquals(2, response.content().size());
        assertEquals(0, response.page().number());
        assertEquals(20, response.page().size());
        assertEquals(2, response.page().totalElements());
        assertEquals(1, response.page().totalPages());
        assertTrue(response.page().first());
        assertTrue(response.page().last());
        assertEquals(1, response.page().sort().size());
    }

    @Test
    void handlesEmptyPage() {
        var response = PageResponse.of(
            List.of(), 0, 20, 0, 0,
            true, true, false, false,
            List.of()
        );
        assertTrue(response.content().isEmpty());
        assertEquals(0, response.page().totalElements());
    }
}
