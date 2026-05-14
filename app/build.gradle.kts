/*
 * Copyright 2023 Pachli Association
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.util.Properties

plugins {
    alias(libs.plugins.pachli.android.application)
    alias(libs.plugins.pachli.android.compose)
    alias(libs.plugins.pachli.android.hilt)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.moshixir)
}

apply(from = "gitTools.gradle")
val getGitSha: groovy.lang.Closure<String> by extra
val getGitRevCount: groovy.lang.Closure<Int> by extra

moshi {
    enableSealed.set(true)
}

android {
    namespace = "app.pachli"

    defaultConfig {
        applicationId = "gratis.anon.social"
        versionCode = 2
        versionName = "0.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["disableAnalytics"] = "true"

        vectorDrawables.useSupportLibrary = true
    }

    // Anon Social — release signing. Reads from app/signing.properties
    // (gitignored). Same pattern as the other Anon apps.
    val signingProps = file("signing.properties")
    if (signingProps.exists()) {
        val props = Properties()
        signingProps.inputStream().use { props.load(it) }
        signingConfigs {
            create("release") {
                storeFile     = file(props.getProperty("keystore"))
                storePassword = props.getProperty("keystore.password")
                keyAlias      = props.getProperty("keystore.alias")
                keyPassword   = props.getProperty("keystore.password")
            }
        }
    }

    buildTypes {
        debug {
            isDefault = true
        }

        release {
            // Anon Social: minify disabled because tor-android's AAR ships no
            // consumer-proguard rules and R8 strips/renames the JNI bridge
            // methods libtor.so calls back into, crashing the app on the first
            // bindService. Matches AnonMail / AnonMumble / AnonWhistleBlower.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingProps.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources.excludes.apply {
            add("LICENSE_OFL")
            add("LICENSE_UNICODE")
        }
    }

    bundle {
        language {
            // bundle all languages in every apk so the dynamic language switching works
            enableSplit = false
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        checkDependencies = true
        // Disable lint for release builds, it's already checked as part of
        // CI, and checking again unnecessarily slows down release builds.
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.all {
            it.systemProperty("robolectric.logging.enabled", "true")
            it.systemProperty("robolectric.lazyload", "ON")
        }
    }

    applicationVariants.configureEach {
        tasks.register("printVersionInfo${name.replaceFirstChar { it.uppercaseChar() }}") {
            notCompatibleWithConfigurationCache("Should always print the version info")
            doLast {
                println("$versionCode $versionName")
            }
        }
        outputs.configureEach {
            this as ApkVariantOutputImpl
            // Set the "orange" release versionCode to the number of commits on the
            // branch, to ensure the versionCode updates on every release. Include the
            // SHA of the current commit to help with troubleshooting bug reports
            if (flavorName.startsWith("orange")) {
                versionNameOverride = "$versionName+${getGitSha()}"
            }
            if (buildType.name == "release" && flavorName.startsWith("orange")) {
                versionCodeOverride = getGitRevCount()
            }
            outputFileName = "Pachli_${versionName}_${versionCode}_${getGitSha()}_${flavorName}_${buildType.name}.apk"
        }
    }
}

configurations {
    // JNI-only libraries don't play nicely with Robolectric
    // see https://github.com/tuskyapp/Tusky/pull/3367 and
    // https://github.com/google/conscrypt/issues/649
    testImplementation {
        exclude(group = "org.conscrypt", module = "conscrypt-android")
    }

    implementation {
        exclude(group = "org.jetbrains", module = "annotations")
    }
}

configurations.configureEach {
    resolutionStrategy {
        // Fix Cannot find a version of 'androidx.test.espresso:espresso-core' that satisfies the version constraints:
        // Fix Cannot find a version of 'androidx.test.ext:junit' that satisfies the version constraints:
        force(libs.espresso.core)
        force(libs.androidx.test.junit)
    }
}

dependencies {
    // CachedTimelineRemoteMediator needs the @Transaction annotation from Room
    compileOnly(libs.bundles.room)
    testCompileOnly(libs.bundles.room)

    // @HiltWorker annotation
    implementation(libs.androidx.hilt.common)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(projects.core.activity)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.eventhub)
    implementation(projects.core.model)
    implementation(projects.core.navigation)
    implementation(projects.core.network)
    implementation(projects.core.preferences)
    implementation(projects.core.sendstatus)
    implementation(projects.core.ui)
    implementation(projects.core.worker)

    implementation(projects.feature.about)
    implementation(projects.feature.drafts)
    implementation(projects.feature.intentrouter)
    implementation(projects.feature.lists)
    implementation(projects.feature.login)
    implementation(projects.feature.manageaccounts)
    implementation(projects.feature.suggestions)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.bundles.androidx)
    implementation(libs.androidx.core.animation)

    implementation(libs.android.material)

    implementation(libs.bundles.retrofit)

    implementation(libs.bundles.okhttp)
    implementation(libs.okio)

    implementation(libs.conscrypt.android)

    ksp(libs.glide.compiler)

    implementation(libs.touchimageview)

    implementation(libs.bundles.material.drawer)
    implementation(libs.material.typeface)

    implementation(libs.image.cropper)

    implementation(libs.bundles.filemojicompat)

    implementation(libs.unified.push)

    // Embedded Tor (same lib as Anon Mail / Mumble / WhistleBlower).
    // Pinned hardcoded rather than via libs.versions.toml since we don't
    // pull anything else from this maven repo; keeps the change isolated.
    implementation("info.guardianproject:tor-android:0.4.8.16")
    implementation("info.guardianproject:jtorctl:0.4.5.7")

    // App-lock (same stack as Anon PGP / Mail / VPN / WhistleBlower).
    // EncryptedSharedPreferences for PIN hash + Biometric prompt for the
    // optional fingerprint/face shortcut. lifecycle-process already comes
    // in transitively from androidx.lifecycle 2.10.0.
    //
    // Excluding `tink-android` because Pachli's stack already pulls the
    // newer unified `tink:1.17.0` artifact for crypto elsewhere, and the
    // two collide at dex time. `tink` (1.17.0) provides the same API
    // surface security-crypto needs at runtime on Android.
    implementation("androidx.security:security-crypto:1.1.0-alpha06") {
        exclude(group = "com.google.crypto.tink", module = "tink-android")
    }
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    implementation(libs.bundles.xmldiff)

    implementation(libs.timber)

    googleImplementation(libs.app.update)
    googleImplementation(libs.app.update.ktx)

    // Language detection
    googleImplementation(libs.play.services.base)
    googleImplementation(libs.mlkit.language.id)
    googleImplementation(libs.kotlinx.coroutines.play.services)

    // Translation
    googleImplementation(libs.mlkit.translation)
    googleImplementation(libs.composeunstyled)

    implementation(libs.semver)

    debugImplementation(libs.leakcanary)

    testImplementation(projects.core.testing)
    testImplementation(projects.core.networkTest)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.bundles.mockito)
    testImplementation(libs.androidx.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.test.core.ktx)

    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)

    lintChecks(projects.checks)
    ktlintRuleset(libs.ktlint.compose.rules)
}
