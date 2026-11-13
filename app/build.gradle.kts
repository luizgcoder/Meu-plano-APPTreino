import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.luiz.meuplano"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luiz.meuplano"
        minSdk = 26
        targetSdk = 36
        versionCode = 23
        versionName = "0.23"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Assinada com a chave de testes, para instalar pelo Android Studio (Build Variants → release).
            // É esta a versão para usar no dia a dia: a "debug" roda bem mais devagar, com travadinhas
            // em rolagem e troca de tela que não existem aqui.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.media3.effect)

    // Montagem do vídeo do feedback (cortar, juntar e legendar)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.annotation.experimental)

    // Miniaturas das fotos
    implementation(libs.coil.compose)

    implementation(libs.jsoup)

    testImplementation(libs.junit)
}
