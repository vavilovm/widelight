plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "com.widelight"
 compileSdk = 35
 defaultConfig { applicationId = "com.widelight"; minSdk = 28; targetSdk = 35; versionCode = 2; versionName = "0.2.0" }
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
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
 implementation("com.google.zxing:core:3.5.3")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.13")
 testImplementation("androidx.test:core:1.6.1")
}
