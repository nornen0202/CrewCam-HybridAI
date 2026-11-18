package com.galaxycrew.crewcam

import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal fun appCheckFactory() = PlayIntegrityAppCheckProviderFactory.getInstance()
