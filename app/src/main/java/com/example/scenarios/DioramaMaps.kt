package com.example.scenarios

import com.example.sim.*

private class GraphDsl(
    val id: String,
    val name: String,
    val worldWidth: Float,
    val worldHeight: Float
) {
    val nodes = LinkedHashMap<String, TrackNode>()
    val segments = LinkedHashMap<String, TrackSegment>()
    val switches = LinkedHashMap<String, TrackSwitch>()
    val signals = LinkedHashMap<String, RailwaySignal>()
    val stations = LinkedHashMap<String, StationInfo>()
    val props = mutableListOf<DioramaProp>()

    fun node(
        id: String,
        x: Float,
        y: Float,
        type: NodeType,
        label: String? = null,
        stationId: String? = null,
        platformNumber: Int? = null
    ) {
        nodes[id] = TrackNode(id, x, y, type, label, stationId, platformNumber)
    }

    fun station(id: String, name: String, code: String, x: Float, y: Float, platformCount: Int) {
        stations[id] = StationInfo(id, name, code, x, y, platformCount)
    }

    fun seg(
        id: String,
        nodeA: String,
        nodeB: String,
        controlPoint: Vec2? = null,
        category: TrackCategory = TrackCategory.MAINLINE,
        speedLimit: Float = 95f,
        allowedDirections: DirectionMode = DirectionMode.BIDIRECTIONAL,
        allowHeavyFreight: Boolean = true,
        stationId: String? = null,
        platformNumber: Int? = null
    ) {
        val a = nodes.getValue(nodeA).position
        val b = nodes.getValue(nodeB).position
        val len = TrackCurve(a, controlPoint, b).totalLength
        segments[id] = TrackSegment(
            id = id,
            nodeA = nodeA,
            nodeB = nodeB,
            controlPoint = controlPoint,
            category = category,
            speedLimit = speedLimit,
            allowedDirections = allowedDirections,
            allowHeavyFreight = allowHeavyFreight,
            stationId = stationId,
            platformNumber = platformNumber,
            length = len
        )
    }

    fun switch(
        id: String,
        nodeId: String,
        stemSegmentId: String,
        branchSegmentIds: List<String>,
        activeBranchIndex: Int = 0,
        label: String
    ) {
        switches[id] = TrackSwitch(
            id = id,
            nodeId = nodeId,
            stemSegmentId = stemSegmentId,
            branchSegmentIds = branchSegmentIds,
            activeBranchIndex = activeBranchIndex,
            label = label,
            animProgress = activeBranchIndex.toFloat()
        )
    }

    fun signal(id: String, nodeId: String, facingFromSegmentId: String, label: String) {
        signals[id] = RailwaySignal(
            id = id,
            nodeId = nodeId,
            facingFromSegmentId = facingFromSegmentId,
            label = label
        )
    }

    fun prop(p: DioramaProp) {
        props.add(p)
    }

    fun build(): TrackGraph {
        val graph = TrackGraph(id, name, worldWidth, worldHeight, nodes, segments, switches, signals, stations, props)
        val errs = graph.validate()
        require(errs.isEmpty()) { "Invalid TrackGraph '$id': ${errs.joinToString("; ")}" }
        return graph
    }
}

object DioramaMaps {

    /**
     * Map 1: Valley Junction — Countryside passing loop & two-platform station.
     * Used for the Interactive Tutorial and Quick Dispatch 1.
     */
    fun valleyJunction(): TrackGraph = GraphDsl(
        id = "valley_junction",
        name = "Valley Junction",
        worldWidth = 1400f,
        worldHeight = 800f
    ).apply {
        station("STN_VALLEY", "Valley Central", "VLY", 725f, 400f, 2)

        node("W_IN", 70f, 400f, NodeType.PORTAL_BIDIRECTIONAL, "West Portal")
        node("W_APP", 250f, 400f, NodeType.JUNCTION, "West Approach")
        node("J_WEST", 410f, 400f, NodeType.JUNCTION, "SW-1")

        node("P1_W", 560f, 280f, NodeType.JUNCTION)
        node("STN_P1", 725f, 280f, NodeType.STATION_STOP, "Platform 1", "STN_VALLEY", 1)
        node("P1_E", 890f, 280f, NodeType.JUNCTION)

        node("P2_W", 560f, 520f, NodeType.JUNCTION)
        node("STN_P2", 725f, 520f, NodeType.STATION_STOP, "Platform 2", "STN_VALLEY", 2)
        node("P2_E", 890f, 520f, NodeType.JUNCTION)

        node("J_EAST", 1040f, 400f, NodeType.JUNCTION, "SW-2")
        node("E_BR", 1190f, 400f, NodeType.JUNCTION)
        node("E_OUT", 1330f, 400f, NodeType.PORTAL_BIDIRECTIONAL, "East Portal")

        seg("w_in_app", "W_IN", "W_APP", category = TrackCategory.MAINLINE, speedLimit = 95f)
        seg("w_app_jw", "W_APP", "J_WEST", category = TrackCategory.MAINLINE, speedLimit = 90f)

        seg("jw_p1w", "J_WEST", "P1_W", controlPoint = Vec2(475f, 280f), category = TrackCategory.MAINLINE, speedLimit = 75f)
        seg("p1_west", "P1_W", "STN_P1", category = TrackCategory.PLATFORM, speedLimit = 65f, stationId = "STN_VALLEY", platformNumber = 1)
        seg("p1_east", "STN_P1", "P1_E", category = TrackCategory.PLATFORM, speedLimit = 65f, stationId = "STN_VALLEY", platformNumber = 1)
        seg("p1e_je", "P1_E", "J_EAST", controlPoint = Vec2(975f, 280f), category = TrackCategory.MAINLINE, speedLimit = 75f)

        seg("jw_p2w", "J_WEST", "P2_W", controlPoint = Vec2(475f, 520f), category = TrackCategory.MAINLINE, speedLimit = 75f)
        seg("p2_west", "P2_W", "STN_P2", category = TrackCategory.PLATFORM, speedLimit = 65f, stationId = "STN_VALLEY", platformNumber = 2)
        seg("p2_east", "STN_P2", "P2_E", category = TrackCategory.PLATFORM, speedLimit = 65f, stationId = "STN_VALLEY", platformNumber = 2)
        seg("p2e_je", "P2_E", "J_EAST", controlPoint = Vec2(975f, 520f), category = TrackCategory.MAINLINE, speedLimit = 75f)

        seg("je_ebr", "J_EAST", "E_BR", category = TrackCategory.VIADUCT, speedLimit = 95f)
        seg("ebr_eout", "E_BR", "E_OUT", category = TrackCategory.MAINLINE, speedLimit = 95f)

        switch("SW_1", "J_WEST", "w_app_jw", listOf("jw_p1w", "jw_p2w"), activeBranchIndex = 0, label = "SW-1")
        switch("SW_2", "J_EAST", "je_ebr", listOf("p1e_je", "p2e_je"), activeBranchIndex = 0, label = "SW-2")

        signal("SIG_W", "J_WEST", "w_app_jw", "W-1")
        signal("SIG_P1W", "J_WEST", "jw_p1w", "P1-W")
        signal("SIG_P2W", "J_WEST", "jw_p2w", "P2-W")
        signal("SIG_P1E", "J_EAST", "p1e_je", "P1-E")
        signal("SIG_P2E", "J_EAST", "p2e_je", "P2-E")
        signal("SIG_E", "J_EAST", "je_ebr", "E-1")

        // Scenery props
        prop(DioramaProp.Hill(210f, 175f, 175f, 95f, 2))
        prop(DioramaProp.Hill(1190f, 165f, 165f, 90f, 2))
        prop(DioramaProp.Hill(230f, 660f, 180f, 95f, 1))
        prop(DioramaProp.River(listOf(Vec2(1105f, 20f), Vec2(1140f, 260f), Vec2(1110f, 440f), Vec2(1155f, 640f), Vec2(1130f, 790f)), 58f))
        prop(DioramaProp.BridgeDeck(1055f, 400f, 1185f, 400f, 38f))
        prop(DioramaProp.Building(725f, 400f, 145f, 54f, BuildingStyle.STATION_HOUSE))
        prop(DioramaProp.Building(360f, 325f, 36f, 28f, BuildingStyle.SIGNAL_BOX))
        prop(DioramaProp.Building(290f, 560f, 56f, 40f, BuildingStyle.COTTAGE, -8f))
        prop(DioramaProp.Building(940f, 645f, 54f, 38f, BuildingStyle.COTTAGE, 10f))
        prop(DioramaProp.TreeCluster(160f, 160f, 9, 11))
        prop(DioramaProp.TreeCluster(980f, 145f, 8, 22))
        prop(DioramaProp.TreeCluster(510f, 680f, 10, 33))
        prop(DioramaProp.TreeCluster(1275f, 620f, 8, 44))
    }.build()

    /**
     * Map 2: Timber Creek — High-speed mainline with passing siding loop for Express Priority.
     */
    fun timberCreek(): TrackGraph = GraphDsl(
        id = "timber_creek",
        name = "Timber Creek",
        worldWidth = 1400f,
        worldHeight = 800f
    ).apply {
        station("STN_TIMBER", "Timber Creek", "TMC", 700f, 250f, 1)

        node("W_IN", 70f, 460f, NodeType.PORTAL_BIDIRECTIONAL, "West Main")
        node("W_APP", 240f, 460f, NodeType.JUNCTION)
        node("J_WEST", 400f, 460f, NodeType.JUNCTION, "SW-1")

        node("MAIN_MID", 700f, 460f, NodeType.JUNCTION)

        node("SID_W", 545f, 290f, NodeType.JUNCTION)
        node("STN_TC", 700f, 290f, NodeType.STATION_STOP, "Platform 1", "STN_TIMBER", 1)
        node("SID_E", 855f, 290f, NodeType.JUNCTION)

        node("J_EAST", 1000f, 460f, NodeType.JUNCTION, "SW-2")
        node("E_APP", 1170f, 460f, NodeType.JUNCTION)
        node("E_OUT", 1330f, 460f, NodeType.PORTAL_BIDIRECTIONAL, "East Main")

        seg("w_in_app", "W_IN", "W_APP", category = TrackCategory.MAINLINE, speedLimit = 125f)
        seg("w_app_jw", "W_APP", "J_WEST", category = TrackCategory.MAINLINE, speedLimit = 120f)
        seg("jw_main", "J_WEST", "MAIN_MID", category = TrackCategory.MAINLINE, speedLimit = 125f)
        seg("main_je", "MAIN_MID", "J_EAST", category = TrackCategory.MAINLINE, speedLimit = 125f)

        seg("jw_sidw", "J_WEST", "SID_W", controlPoint = Vec2(455f, 290f), category = TrackCategory.SIDING, speedLimit = 62f)
        seg("sid_w_stn", "SID_W", "STN_TC", category = TrackCategory.PLATFORM, speedLimit = 58f, stationId = "STN_TIMBER", platformNumber = 1)
        seg("stn_sid_e", "STN_TC", "SID_E", category = TrackCategory.PLATFORM, speedLimit = 58f, stationId = "STN_TIMBER", platformNumber = 1)
        seg("side_je", "SID_E", "J_EAST", controlPoint = Vec2(945f, 290f), category = TrackCategory.SIDING, speedLimit = 62f)

        seg("je_eapp", "J_EAST", "E_APP", category = TrackCategory.VIADUCT, speedLimit = 120f)
        seg("eapp_eout", "E_APP", "E_OUT", category = TrackCategory.MAINLINE, speedLimit = 125f)

        switch("SW_1", "J_WEST", "w_app_jw", listOf("jw_main", "jw_sidw"), activeBranchIndex = 0, label = "SW-1")
        switch("SW_2", "J_EAST", "je_eapp", listOf("main_je", "side_je"), activeBranchIndex = 0, label = "SW-2")

        signal("SIG_W", "J_WEST", "w_app_jw", "W-1")
        signal("SIG_MAIN_W", "J_WEST", "jw_main", "M-W")
        signal("SIG_SID_W", "J_WEST", "jw_sidw", "S-W")
        signal("SIG_MAIN_E", "J_EAST", "main_je", "M-E")
        signal("SIG_SID_E", "J_EAST", "side_je", "S-E")
        signal("SIG_E", "J_EAST", "je_eapp", "E-1")

        prop(DioramaProp.Hill(210f, 180f, 170f, 100f, 2))
        prop(DioramaProp.Hill(1180f, 640f, 185f, 105f, 2))
        prop(DioramaProp.River(listOf(Vec2(1075f, 25f), Vec2(1095f, 280f), Vec2(1080f, 470f), Vec2(1115f, 780f)), 54f))
        prop(DioramaProp.BridgeDeck(1020f, 460f, 1155f, 460f, 36f))
        prop(DioramaProp.Building(700f, 215f, 128f, 44f, BuildingStyle.STATION_HOUSE))
        prop(DioramaProp.Building(365f, 525f, 34f, 26f, BuildingStyle.SIGNAL_BOX))
        prop(DioramaProp.TreeCluster(155f, 635f, 11, 55))
        prop(DioramaProp.TreeCluster(540f, 145f, 8, 66))
        prop(DioramaProp.TreeCluster(1255f, 200f, 9, 77))
    }.build()

    /**
     * Map 3: Foundry Siding — Industrial District with Passenger Platform (restricted from Heavy Freight)
     * plus Freight Bypass and Foundry Freight Bay Terminus.
     */
    fun foundrySiding(): TrackGraph = GraphDsl(
        id = "foundry_siding",
        name = "Foundry District",
        worldWidth = 1400f,
        worldHeight = 820f
    ).apply {
        station("STN_FOUNDRY", "Foundry Halt", "FND", 700f, 250f, 1)
        station("STN_BAY", "Foundry Freight Bay", "BAY", 920f, 620f, 1)

        node("W_IN", 70f, 410f, NodeType.PORTAL_BIDIRECTIONAL, "West Yard")
        node("W_APP", 230f, 410f, NodeType.JUNCTION)
        node("J_WEST", 390f, 410f, NodeType.JUNCTION, "SW-1")

        // Upper Passenger Corridor (allowHeavyFreight = false!)
        node("P1_W", 545f, 260f, NodeType.JUNCTION)
        node("STN_P1", 700f, 260f, NodeType.STATION_STOP, "Passenger Platform", "STN_FOUNDRY", 1)
        node("P1_E", 855f, 260f, NodeType.JUNCTION)

        // Lower Freight Corridor -> splits at SW-3 to Freight Bypass OR Freight Bay Buffer!
        node("FRT_MID", 540f, 490f, NodeType.JUNCTION)
        node("J_FRT", 680f, 490f, NodeType.JUNCTION, "SW-3")
        node("BYPASS_E", 860f, 490f, NodeType.JUNCTION)

        node("BAY_APPROACH", 805f, 620f, NodeType.JUNCTION)
        node("FRT_BAY", 1010f, 620f, NodeType.BUFFER, "Freight Bay", "STN_BAY", 1)

        node("J_EAST", 1020f, 410f, NodeType.JUNCTION, "SW-2")
        node("E_OUT", 1330f, 410f, NodeType.PORTAL_BIDIRECTIONAL, "East Main")

        seg("w_in_app", "W_IN", "W_APP", category = TrackCategory.MAINLINE, speedLimit = 85f)
        seg("w_app_jw", "W_APP", "J_WEST", category = TrackCategory.MAINLINE, speedLimit = 85f)

        seg("jw_p1w", "J_WEST", "P1_W", controlPoint = Vec2(450f, 260f), category = TrackCategory.MAINLINE, speedLimit = 70f, allowHeavyFreight = false)
        seg("p1_w_stn", "P1_W", "STN_P1", category = TrackCategory.PLATFORM, speedLimit = 60f, allowHeavyFreight = false, stationId = "STN_FOUNDRY", platformNumber = 1)
        seg("p1_stn_e", "STN_P1", "P1_E", category = TrackCategory.PLATFORM, speedLimit = 60f, allowHeavyFreight = false, stationId = "STN_FOUNDRY", platformNumber = 1)
        seg("p1e_je", "P1_E", "J_EAST", controlPoint = Vec2(955f, 260f), category = TrackCategory.MAINLINE, speedLimit = 70f, allowHeavyFreight = false)

        seg("jw_frtmid", "J_WEST", "FRT_MID", controlPoint = Vec2(455f, 490f), category = TrackCategory.MAINLINE, speedLimit = 75f)
        seg("frtmid_jfrt", "FRT_MID", "J_FRT", category = TrackCategory.MAINLINE, speedLimit = 75f)
        seg("jfrt_bypasse", "J_FRT", "BYPASS_E", category = TrackCategory.MAINLINE, speedLimit = 75f)
        seg("bypasse_je", "BYPASS_E", "J_EAST", controlPoint = Vec2(955f, 490f), category = TrackCategory.MAINLINE, speedLimit = 75f)

        seg("jfrt_bayapp", "J_FRT", "BAY_APPROACH", controlPoint = Vec2(725f, 620f), category = TrackCategory.SIDING, speedLimit = 50f)
        seg("bayapp_bay", "BAY_APPROACH", "FRT_BAY", category = TrackCategory.SIDING, speedLimit = 45f, stationId = "STN_BAY", platformNumber = 1)

        seg("je_eout", "J_EAST", "E_OUT", category = TrackCategory.MAINLINE, speedLimit = 88f)

        switch("SW_1", "J_WEST", "w_app_jw", listOf("jw_p1w", "jw_frtmid"), activeBranchIndex = 0, label = "SW-1")
        switch("SW_2", "J_EAST", "je_eout", listOf("p1e_je", "bypasse_je"), activeBranchIndex = 0, label = "SW-2")
        switch("SW_3", "J_FRT", "frtmid_jfrt", listOf("jfrt_bypasse", "jfrt_bayapp"), activeBranchIndex = 1, label = "SW-3")

        signal("SIG_W", "J_WEST", "w_app_jw", "W-1")
        signal("SIG_P1E", "J_EAST", "p1e_je", "P1-E")
        signal("SIG_FRT", "J_FRT", "frtmid_jfrt", "F-1")
        signal("SIG_BYP_E", "J_EAST", "bypasse_je", "B-E")
        signal("SIG_E", "J_EAST", "je_eout", "E-1")

        prop(DioramaProp.Hill(165f, 170f, 140f, 85f, 1))
        prop(DioramaProp.River(listOf(Vec2(1195f, 20f), Vec2(1225f, 310f), Vec2(1190f, 560f), Vec2(1235f, 800f)), 58f))
        prop(DioramaProp.BridgeDeck(1140f, 410f, 1270f, 410f, 36f))
        prop(DioramaProp.Building(700f, 180f, 125f, 42f, BuildingStyle.STATION_HOUSE))
        prop(DioramaProp.Building(925f, 700f, 165f, 65f, BuildingStyle.WAREHOUSE))
        prop(DioramaProp.Building(615f, 625f, 46f, 46f, BuildingStyle.WATER_TOWER))
        prop(DioramaProp.Building(340f, 470f, 34f, 26f, BuildingStyle.SIGNAL_BOX))
        prop(DioramaProp.TreeCluster(185f, 650f, 8, 81))
        prop(DioramaProp.TreeCluster(1055f, 165f, 7, 92))
    }.build()

    /**
     * Map 4: Twin Viaduct Gorge — Two mountain viaducts and central passing loop with 4 switches.
     */
    fun twinViaduct(): TrackGraph = GraphDsl(
        id = "twin_viaduct",
        name = "Twin Viaduct Gorge",
        worldWidth = 1500f,
        worldHeight = 840f
    ).apply {
        station("STN_GORGE_N", "North Gorge", "NRG", 750f, 260f, 1)
        station("STN_GORGE_S", "South Gorge", "SRG", 750f, 580f, 1)

        node("W_IN", 70f, 420f, NodeType.PORTAL_BIDIRECTIONAL, "West Gorge")
        node("W_VIADUCT", 240f, 420f, NodeType.JUNCTION)
        node("J_WEST", 410f, 420f, NodeType.JUNCTION, "SW-1")

        node("N_W", 560f, 260f, NodeType.JUNCTION)
        node("STN_N", 750f, 260f, NodeType.STATION_STOP, "North Platform", "STN_GORGE_N", 1)
        node("N_E", 940f, 260f, NodeType.JUNCTION)

        node("S_W", 560f, 580f, NodeType.JUNCTION)
        node("STN_S", 750f, 580f, NodeType.STATION_STOP, "South Platform", "STN_GORGE_S", 1)
        node("S_E", 940f, 580f, NodeType.JUNCTION)

        node("J_EAST", 1090f, 420f, NodeType.JUNCTION, "SW-2")
        node("E_VIADUCT", 1260f, 420f, NodeType.JUNCTION)
        node("E_OUT", 1430f, 420f, NodeType.PORTAL_BIDIRECTIONAL, "East Gorge")

        seg("w_in_v", "W_IN", "W_VIADUCT", category = TrackCategory.VIADUCT, speedLimit = 95f)
        seg("w_v_jw", "W_VIADUCT", "J_WEST", category = TrackCategory.MAINLINE, speedLimit = 90f)

        seg("jw_nw", "J_WEST", "N_W", controlPoint = Vec2(470f, 260f), category = TrackCategory.MAINLINE, speedLimit = 80f)
        seg("nw_stnn", "N_W", "STN_N", category = TrackCategory.PLATFORM, speedLimit = 68f, stationId = "STN_GORGE_N", platformNumber = 1)
        seg("stnn_ne", "STN_N", "N_E", category = TrackCategory.PLATFORM, speedLimit = 68f, stationId = "STN_GORGE_N", platformNumber = 1)
        seg("ne_je", "N_E", "J_EAST", controlPoint = Vec2(1030f, 260f), category = TrackCategory.MAINLINE, speedLimit = 80f)

        seg("jw_sw", "J_WEST", "S_W", controlPoint = Vec2(470f, 580f), category = TrackCategory.MAINLINE, speedLimit = 80f)
        seg("sw_stns", "S_W", "STN_S", category = TrackCategory.PLATFORM, speedLimit = 68f, stationId = "STN_GORGE_S", platformNumber = 1)
        seg("stns_se", "STN_S", "S_E", category = TrackCategory.PLATFORM, speedLimit = 68f, stationId = "STN_GORGE_S", platformNumber = 1)
        seg("se_je", "S_E", "J_EAST", controlPoint = Vec2(1030f, 580f), category = TrackCategory.MAINLINE, speedLimit = 80f)

        seg("je_ev", "J_EAST", "E_VIADUCT", category = TrackCategory.VIADUCT, speedLimit = 95f)
        seg("ev_eout", "E_VIADUCT", "E_OUT", category = TrackCategory.MAINLINE, speedLimit = 95f)

        switch("SW_1", "J_WEST", "w_v_jw", listOf("jw_nw", "jw_sw"), activeBranchIndex = 0, label = "SW-1")
        switch("SW_2", "J_EAST", "je_ev", listOf("ne_je", "se_je"), activeBranchIndex = 0, label = "SW-2")

        signal("SIG_W", "J_WEST", "w_v_jw", "W-1")
        signal("SIG_NE", "J_EAST", "ne_je", "N-E")
        signal("SIG_SE", "J_EAST", "se_je", "S-E")
        signal("SIG_E", "J_EAST", "je_ev", "E-1")

        prop(DioramaProp.Hill(750f, 420f, 210f, 95f, 3))
        prop(DioramaProp.Hill(220f, 160f, 180f, 95f, 2))
        prop(DioramaProp.Hill(1280f, 680f, 190f, 100f, 2))
        prop(DioramaProp.River(listOf(Vec2(140f, 20f), Vec2(175f, 260f), Vec2(150f, 490f), Vec2(190f, 820f)), 52f))
        prop(DioramaProp.River(listOf(Vec2(1240f, 20f), Vec2(1210f, 290f), Vec2(1250f, 540f), Vec2(1225f, 820f)), 56f))
        prop(DioramaProp.BridgeDeck(95f, 420f, 225f, 420f, 36f))
        prop(DioramaProp.BridgeDeck(1165f, 420f, 1300f, 420f, 36f))
        prop(DioramaProp.Building(750f, 190f, 118f, 40f, BuildingStyle.STATION_HOUSE))
        prop(DioramaProp.Building(750f, 650f, 118f, 40f, BuildingStyle.STATION_HOUSE))
        prop(DioramaProp.TreeCluster(500f, 145f, 10, 101))
        prop(DioramaProp.TreeCluster(1010f, 685f, 10, 102))
    }.build()

    /**
     * Map 5: Alderford Central Network — Rich multi-corridor Rush Hour & Zen Dispatch layout.
     * Features 2 west portals (Main & Tunnel), 3-platform Alderford Central, River Viaduct, and 2 east branches.
     */
    fun alderfordNetwork(): TrackGraph = GraphDsl(
        id = "alderford_network",
        name = "Alderford Central",
        worldWidth = 1640f,
        worldHeight = 900f
    ).apply {
        station("STN_ALDERFORD", "Alderford Central", "ALD", 800f, 450f, 3)
        station("STN_PINEWOOD", "Pinewood Halt", "PNW", 1380f, 230f, 1)

        // West Portals & West Throat
        node("W_MAIN_IN", 70f, 520f, NodeType.PORTAL_BIDIRECTIONAL, "West Main")
        node("W_TUNNEL_IN", 95f, 230f, NodeType.PORTAL_BIDIRECTIONAL, "North Tunnel")
        node("J_W_ENTRY", 290f, 450f, NodeType.JUNCTION, "SW-1")
        node("W_THROAT", 435f, 450f, NodeType.JUNCTION, "SW-2")
        node("W_LOWER", 525f, 575f, NodeType.JUNCTION, "SW-3")

        // 3 Platforms at Alderford Central
        node("P1_W", 635f, 270f, NodeType.JUNCTION)
        node("STN_P1", 800f, 270f, NodeType.STATION_STOP, "Platform 1 (Express)", "STN_ALDERFORD", 1)
        node("P1_E", 965f, 270f, NodeType.JUNCTION)

        node("P2_W", 635f, 450f, NodeType.JUNCTION)
        node("STN_P2", 800f, 450f, NodeType.STATION_STOP, "Platform 2 (Local)", "STN_ALDERFORD", 2)
        node("P2_E", 965f, 450f, NodeType.JUNCTION)

        node("P3_W", 635f, 630f, NodeType.JUNCTION)
        node("STN_P3", 800f, 630f, NodeType.STATION_STOP, "Platform 3 (Freight)", "STN_ALDERFORD", 3)
        node("P3_E", 965f, 630f, NodeType.JUNCTION)

        // East Throat & Viaduct Split
        node("E_LOWER", 1075f, 575f, NodeType.JUNCTION, "SW-4")
        node("E_THROAT", 1165f, 450f, NodeType.JUNCTION, "SW-5")
        node("J_E_SPLIT", 1310f, 450f, NodeType.JUNCTION, "SW-6")

        node("STN_PNW", 1420f, 250f, NodeType.STATION_STOP, "Pinewood Halt", "STN_PINEWOOD", 1)
        node("NE_OUT", 1570f, 220f, NodeType.PORTAL_BIDIRECTIONAL, "North-East Branch")
        node("SE_OUT", 1570f, 620f, NodeType.PORTAL_BIDIRECTIONAL, "Harbor Mainline")

        // West approach tracks
        seg("wm_jw", "W_MAIN_IN", "J_W_ENTRY", controlPoint = Vec2(190f, 520f), category = TrackCategory.MAINLINE, speedLimit = 100f)
        seg("wt_jw", "W_TUNNEL_IN", "J_W_ENTRY", controlPoint = Vec2(215f, 230f), category = TrackCategory.TUNNEL, speedLimit = 85f)
        seg("jw_wthroat", "J_W_ENTRY", "W_THROAT", category = TrackCategory.MAINLINE, speedLimit = 95f)

        // West throat to P1 / lower split
        seg("wthroat_p1w", "W_THROAT", "P1_W", controlPoint = Vec2(510f, 270f), category = TrackCategory.MAINLINE, speedLimit = 85f)
        seg("wthroat_wlower", "W_THROAT", "W_LOWER", controlPoint = Vec2(465f, 550f), category = TrackCategory.MAINLINE, speedLimit = 78f)
        seg("wlower_p2w", "W_LOWER", "P2_W", controlPoint = Vec2(575f, 450f), category = TrackCategory.MAINLINE, speedLimit = 72f)
        seg("wlower_p3w", "W_LOWER", "P3_W", controlPoint = Vec2(575f, 630f), category = TrackCategory.SIDING, speedLimit = 60f)

        // Platforms
        seg("p1_w_s", "P1_W", "STN_P1", category = TrackCategory.PLATFORM, speedLimit = 75f, allowHeavyFreight = false, stationId = "STN_ALDERFORD", platformNumber = 1)
        seg("p1_s_e", "STN_P1", "P1_E", category = TrackCategory.PLATFORM, speedLimit = 75f, allowHeavyFreight = false, stationId = "STN_ALDERFORD", platformNumber = 1)

        seg("p2_w_s", "P2_W", "STN_P2", category = TrackCategory.PLATFORM, speedLimit = 68f, stationId = "STN_ALDERFORD", platformNumber = 2)
        seg("p2_s_e", "STN_P2", "P2_E", category = TrackCategory.PLATFORM, speedLimit = 68f, stationId = "STN_ALDERFORD", platformNumber = 2)

        seg("p3_w_s", "P3_W", "STN_P3", category = TrackCategory.PLATFORM, speedLimit = 58f, stationId = "STN_ALDERFORD", platformNumber = 3)
        seg("p3_s_e", "STN_P3", "P3_E", category = TrackCategory.PLATFORM, speedLimit = 58f, stationId = "STN_ALDERFORD", platformNumber = 3)

        // East throat
        seg("p1e_ethroat", "P1_E", "E_THROAT", controlPoint = Vec2(1090f, 270f), category = TrackCategory.MAINLINE, speedLimit = 85f)
        seg("p2e_elower", "P2_E", "E_LOWER", controlPoint = Vec2(1025f, 450f), category = TrackCategory.MAINLINE, speedLimit = 72f)
        seg("p3e_elower", "P3_E", "E_LOWER", controlPoint = Vec2(1025f, 630f), category = TrackCategory.SIDING, speedLimit = 60f)
        seg("elower_ethroat", "E_LOWER", "E_THROAT", controlPoint = Vec2(1135f, 550f), category = TrackCategory.MAINLINE, speedLimit = 78f)

        seg("ethroat_jesplit", "E_THROAT", "J_E_SPLIT", category = TrackCategory.VIADUCT, speedLimit = 95f)
        seg("je_pnw", "J_E_SPLIT", "STN_PNW", controlPoint = Vec2(1345f, 275f), category = TrackCategory.MAINLINE, speedLimit = 75f)
        seg("pnw_neout", "STN_PNW", "NE_OUT", category = TrackCategory.MAINLINE, speedLimit = 80f, stationId = "STN_PINEWOOD", platformNumber = 1)
        seg("je_seout", "J_E_SPLIT", "SE_OUT", controlPoint = Vec2(1415f, 615f), category = TrackCategory.MAINLINE, speedLimit = 105f)

        switch("SW_1", "J_W_ENTRY", "jw_wthroat", listOf("wm_jw", "wt_jw"), activeBranchIndex = 0, label = "SW-1")
        switch("SW_2", "W_THROAT", "jw_wthroat", listOf("wthroat_p1w", "wthroat_wlower"), activeBranchIndex = 0, label = "SW-2")
        switch("SW_3", "W_LOWER", "wthroat_wlower", listOf("wlower_p2w", "wlower_p3w"), activeBranchIndex = 0, label = "SW-3")
        switch("SW_4", "E_LOWER", "elower_ethroat", listOf("p2e_elower", "p3e_elower"), activeBranchIndex = 0, label = "SW-4")
        switch("SW_5", "E_THROAT", "ethroat_jesplit", listOf("p1e_ethroat", "elower_ethroat"), activeBranchIndex = 0, label = "SW-5")
        switch("SW_6", "J_E_SPLIT", "ethroat_jesplit", listOf("je_pnw", "je_seout"), activeBranchIndex = 1, label = "SW-6")

        signal("SIG_WM", "J_W_ENTRY", "wm_jw", "W-M")
        signal("SIG_WT", "J_W_ENTRY", "wt_jw", "W-T")
        signal("SIG_WT_APP", "W_THROAT", "jw_wthroat", "W-TH")
        signal("SIG_P1E", "E_THROAT", "p1e_ethroat", "P1-E")
        signal("SIG_P2E", "E_LOWER", "p2e_elower", "P2-E")
        signal("SIG_P3E", "E_LOWER", "p3e_elower", "P3-E")
        signal("SIG_ESPLIT", "J_E_SPLIT", "ethroat_jesplit", "E-J")
        signal("SIG_NE", "J_E_SPLIT", "je_pnw", "NE-1")
        signal("SIG_SE", "J_E_SPLIT", "je_seout", "SE-1")

        prop(DioramaProp.Hill(210f, 145f, 195f, 115f, 3))
        prop(DioramaProp.Hill(240f, 750f, 200f, 100f, 2))
        prop(DioramaProp.Hill(1465f, 775f, 185f, 95f, 2))
        prop(DioramaProp.River(listOf(Vec2(1215f, 20f), Vec2(1250f, 290f), Vec2(1220f, 520f), Vec2(1265f, 880f)), 62f))
        prop(DioramaProp.BridgeDeck(1175f, 450f, 1295f, 450f, 38f))
        prop(DioramaProp.Building(800f, 360f, 165f, 46f, BuildingStyle.STATION_HOUSE))
        prop(DioramaProp.Building(1420f, 185f, 95f, 36f, BuildingStyle.STATION_HOUSE))
        prop(DioramaProp.Building(395f, 375f, 36f, 26f, BuildingStyle.SIGNAL_BOX))
        prop(DioramaProp.Building(1140f, 515f, 36f, 26f, BuildingStyle.SIGNAL_BOX))
        prop(DioramaProp.Building(800f, 725f, 150f, 54f, BuildingStyle.WAREHOUSE))
        prop(DioramaProp.TreeCluster(440f, 145f, 11, 201))
        prop(DioramaProp.TreeCluster(1040f, 145f, 9, 202))
        prop(DioramaProp.TreeCluster(520f, 790f, 12, 203))
        prop(DioramaProp.TreeCluster(1470f, 435f, 9, 204))
    }.build()
}
