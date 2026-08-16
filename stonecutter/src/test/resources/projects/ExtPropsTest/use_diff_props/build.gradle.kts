when (sc.current.project) {
    "1.21-fabric" -> check(sc.properties["deps.minecraft"] == "1.21.1")
    "26.1-snapshot-7-fabric" -> check(sc.properties["deps.minecraft"] == "26.1-snapshot-7")
}
