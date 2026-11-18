package com.galaxycrew.crewcam

import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun appCheckFactory() = DebugAppCheckProviderFactory.getInstance()
