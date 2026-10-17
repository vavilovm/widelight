plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.widelight"
 compileSdk = 35
 defaultConfig { applicationId = "com.widelight"; minSdk = 28; targetSdk = 35; versionCode = providers.gradleProperty("buildNumber").orNull?.toInt()?.let { 1000 + it } ?: 3; versionName = "0.3.0" + (providers.gradleProperty("buildNumber").orNull?.let { "+$it" } ?: "") }
 defaultConfig { resourceConfigurations += listOf("en", "ru") }
 signingConfigs {
  create("release") {
   val privateStore = System.getenv("ANDROID_KEYSTORE_PATH")
   storeFile = if (privateStore.isNullOrBlank()) rootProject.file(".github/keys/development.p12") else file(privateStore)
   storePassword = if (privateStore.isNullOrBlank()) "android" else System.getenv("ANDROID_KEYSTORE_PASSWORD")
   keyAlias = if (privateStore.isNullOrBlank()) "widelight-development" else System.getenv("ANDROID_KEY_ALIAS")
   keyPassword = if (privateStore.isNullOrBlank()) "android" else System.getenv("ANDROID_KEY_PASSWORD")
  }
 }
 buildTypes { getByName("release") {
  signingConfig = signingConfigs.getByName("release")
  isMinifyEnabled = true
  isShrinkResources = true
  proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
 } }
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.06.00"))
 implementation("androidx.activity:activity-compose:1.9.0")
 implementation("androidx.core:core-ktx:1.13.1")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("com.google.zxing:core:3.5.3")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.13")
 testImplementation("androidx.test:core:1.6.1")
 testImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
