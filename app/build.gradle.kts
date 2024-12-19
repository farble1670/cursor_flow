plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "org.jtb.cursorflow"
  compileSdk = 35

  defaultConfig {
    applicationId = "org.jtb.cursorflow"
    minSdk = 29
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
}

dependencies {

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
}