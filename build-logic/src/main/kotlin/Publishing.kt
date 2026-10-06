import co.uzzu.dotenv.gradle.DotEnvRoot
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.signing.SigningExtension

fun Project.publishArtifact(
    artifact: String,
    title: String,
    summary: String,
    env: (String) -> String? = { the<DotEnvRoot>().fetchOrNull(it) },
    configure: MavenPublication.() -> Unit = {}
) {
    pluginManager.apply("maven-publish")
    pluginManager.apply("signing")

    extensions.configure<PublishingExtension> {
        val pass = env("MVN_KIKUGIE_PASSWORD")
        if (pass == null) println("Missing credentials for kikugie maven repository")
        else repositories.maven {
            name = "KikuGieMaven"
            url = when {
                '-' in version.toString() -> uri("https://maven.kikugie.dev/snapshots")
                else -> uri("https://maven.kikugie.dev/releases")
            }
            credentials {
                username = "kikugie"
                password = pass
            }
        }

        publications.register<MavenPublication>("maven") {
            groupId = "dev.kikugie"
            artifactId = artifact
            version = project.version.toString()
            from(components["java"])
            configure()

            pom {
                name = title
                description = summary
                url = "https://stonecutter.kikugie.dev/"

                developers {
                    developer {
                        id = "kikugie"
                        name = "KikuGie"
                        email = "kikugie@duck.com"
                    }
                }

                licenses {
                    license {
                        name = "GNU Lesser Public License 3.0"
                        url = "https://www.gnu.org/licenses/lgpl-3.0.en.html"
                    }
                }

                scm {
                    connection = "scm:git:git:https://codeberg.org/stonecutter/stonecutter.git"
                    developerConnection = "scm:git:ssh://codeberg.org:stonecutter/stonecutter.git"
                    url = "https://codeberg.org/stonecutter/stonecutter"
                }
            }
        }
    }

    extensions.configure<SigningExtension> {
        env("SIGNING_KEY")?.let {
            useInMemoryPgpKeys(it, env("SIGNING_PASSWORD") ?: "")
        }
        sign(configurations["runtimeElements"])
    }
}
