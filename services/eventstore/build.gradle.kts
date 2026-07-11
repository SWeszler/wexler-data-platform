plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("com.gradleup.shadow") version "8.3.5"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.apache.parquet:parquet-avro:1.14.4")
    implementation("org.apache.hadoop:hadoop-common:3.3.6")
    implementation("org.apache.hadoop:hadoop-aws:3.3.6")
    implementation("com.amazonaws:aws-java-sdk-bundle:1.12.367")
    implementation("io.trino:trino-jdbc:476")
    implementation("org.apache.avro:avro:1.12.0")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.slf4j:slf4j-api:2.0.9")
}

tasks.shadowJar {
    archiveBaseName.set("eventstore")
    archiveClassifier.set("all")
    archiveVersion.set("")
    isZip64 = true
    manifest {
        attributes("Main-Class" to "com.example.eventstore.MainKt")
    }
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
