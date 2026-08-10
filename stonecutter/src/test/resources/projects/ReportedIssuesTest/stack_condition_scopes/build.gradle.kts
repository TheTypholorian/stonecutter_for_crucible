plugins {
    java
}

stonecutter {
    replacements {
        string(current.parsed > "26") {
            replace("accessWidener v2 named", "accessWidener v2 official")
            replace("GuiGraphics", "GuiGraphicsExtractor")
            replace("net.minecraft.client.resources.model.AtlasManager", "net.minecraft.client.resources.model.sprite.AtlasManager")
        }

        string(current.parsed >= "26.3") {
            replace("com.mojang.blaze3d.pipeline", "com.mojang.renderpearl.api.pipeline")
            replace("com/mojang/blaze3d/pipeline", "com/mojang/renderpearl/api/pipeline")
            replace("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")

            replace("#moj_import", "#include")
        }
    }
}