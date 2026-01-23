import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class AntlrExtension {
    abstract val sourceSets: NamedDomainObjectContainer<AntlrOptions>

    abstract class AntlrOptions : Named {
        abstract val longMessages: Property<Boolean>
        abstract val visitor: Property<Boolean>
        abstract val listener: Property<Boolean>
        abstract val libs: ListProperty<String>

        init {
            longMessages.convention(true)
            visitor.convention(false)
            listener.convention(false)
            libs.convention(mutableListOf())
        }
    }
}