package com.example.a2uicomposelabs.androidxa2ui

// The booking form, written twice.
//
// Left column of the pair: the wire androidx.a2ui accepts today. Its parser takes exactly two
// version strings, "v0.9" and "v0.9.1", and refuses anything else, so a v1.0 message never
// gets past the front door.
//
// Right column: the wire our own renderer speaks, spec v1.0.
//
// Both carry the SAME catalogId. That is what makes the pair a fair comparison: the catalog is
// identical, so the only thing differing between the two columns is the protocol.
//
// Both hit a real renderer. Nothing below is a mock-up for a slide.

private const val PHONE_PATTERN = "^010-[0-9]{4}-[0-9]{4}${'$'}"

// ---------------------------------------------------------------- booking, androidx v0.9.1

val BookingAndroidxScript: List<String> =
    listOf(
        """{"version":"v0.9.1","createSurface":{"surfaceId":"booking","catalogId":"$BOOKING_CATALOG_ID","sendDataModel":true}}""",
        // The data model has to be its own message here. In v1.0 it rides along with createSurface.
        """{"version":"v0.9.1","updateDataModel":{"surfaceId":"booking","path":"/","value":{"resv":{"when":"2026-09-11T19:00","people":2,"name":"","phone":""}}}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"booking","components":[{"id":"root","component":"Card","child":"form"},{"id":"form","component":"Column","children":["title","when","people","name","phone","submit"]},{"id":"title","component":"Text","text":"Confirm your booking"}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"booking","components":[{"id":"when","component":"DateTimeInput","label":"Date & time","value":{"path":"/resv/when"},"enableDate":true,"enableTime":true},{"id":"people","component":"Slider","label":"Party size","value":{"path":"/resv/people"},"min":1,"max":8}]}}""",
        // No checks array in this dialect: 'valid' is a property this app's TextField invented,
        // and the engine evaluates the call tree because 'required' is in the catalog's functions.
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"booking","components":[{"id":"name","component":"TextField","label":"Name","text":{"path":"/resv/name"},"valid":{"call":"required","args":{"value":{"path":"/resv/name"}}},"error":"Enter a name"}]}}""",
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"booking","components":[{"id":"phone","component":"TextField","label":"Phone","text":{"path":"/resv/phone"},"valid":{"call":"regex","args":{"value":{"path":"/resv/phone"},"pattern":"$PHONE_PATTERN"}},"error":"Use the 010-1234-5678 format"}]}}""",
        // A labelled button is two components, and the action is wrapped in "event".
        """{"version":"v0.9.1","updateComponents":{"surfaceId":"booking","components":[{"id":"submit","component":"Button","child":"submit_label","variant":"primary","action":{"event":{"name":"confirm_reservation","context":{"when":{"path":"/resv/when"},"people":{"path":"/resv/people"},"name":{"path":"/resv/name"},"phone":{"path":"/resv/phone"}}}}},{"id":"submit_label","component":"Text","text":"Book"}]}}""",
    )

// ---------------------------------------------------------------- booking, spec v1.0

val BookingV1Script: List<String> =
    listOf(
        // Components and data in the opening message: the card is never briefly empty.
        """{"version":"v1.0","createSurface":{"surfaceId":"booking","catalogId":"$BOOKING_CATALOG_ID","components":[{"id":"root","component":"Card","children":["form"]}],"dataModel":{"resv":{"when":"2026-09-11T19:00","people":2,"name":"","phone":""}}}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"booking","components":[{"id":"form","component":"Column","children":["title","when","people","name","phone","submit"]},{"id":"title","component":"Text","text":"Confirm your booking"}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"booking","components":[{"id":"when","component":"DateTimeInput","label":"Date & time","value":{"path":"/resv/when"},"enableDate":true,"enableTime":true},{"id":"people","component":"Slider","label":"Party size","value":{"path":"/resv/people"},"min":1,"max":8}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"booking","components":[{"id":"name","component":"TextField","label":"Name","text":{"path":"/resv/name"},"checks":[{"condition":{"call":"required","args":{"value":{"path":"/resv/name"}}},"message":"Enter a name"}]}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"booking","components":[{"id":"phone","component":"TextField","label":"Phone","text":{"path":"/resv/phone"},"checks":[{"condition":{"call":"regex","args":{"value":{"path":"/resv/phone"},"pattern":"$PHONE_PATTERN"}},"message":"Use the 010-1234-5678 format"}]}]}}""",
        """{"version":"v1.0","updateComponents":{"surfaceId":"booking","components":[{"id":"submit","component":"Button","label":"Book","action":{"name":"confirm_reservation","context":{"when":{"path":"/resv/when"},"people":{"path":"/resv/people"},"name":{"path":"/resv/name"},"phone":{"path":"/resv/phone"}}}}]}}""",
    )
