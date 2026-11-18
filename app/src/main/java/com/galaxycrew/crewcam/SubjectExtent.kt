package com.galaxycrew.crewcam

import com.google.firebase.ai.type.Schema

/** Firebase applies GenerationConfig to its cloud provider. Both routes pass the same strict parser. */
internal object SubjectExtent {
    val cloudSchema = Schema.obj(mapOf(
        "evidence" to Schema.string("Two to six words describing what is visible."),
        "target_visible" to Schema.boolean(),
        "extent_clear" to Schema.boolean(),
        "box_2d" to Schema.array(Schema.integer(), description = "Top, left, bottom, right in 0..1000", minItems = 4, maxItems = 4),
        "frame_edge_cut" to Schema.boolean()
    ))
}
