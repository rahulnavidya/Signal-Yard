package com.example.sim

enum class TrainCategory(
    val displayName: String,
    val shortCode: String,
    val maxSpeed: Float,
    val acceleration: Float,
    val brakingDecel: Float,
    val carriageCount: Int,
    val totalLength: Float,
    val priority: Int,
    val dwellSeconds: Float
) {
    LOCAL_PASSENGER(
        displayName = "Local Passenger",
        shortCode = "LOC",
        maxSpeed = 82f,
        acceleration = 40f,
        brakingDecel = 55f,
        carriageCount = 2,
        totalLength = 94f,
        priority = 1,
        dwellSeconds = 3.2f
    ),
    EXPRESS_PASSENGER(
        displayName = "Express",
        shortCode = "EXP",
        maxSpeed = 122f,
        acceleration = 36f,
        brakingDecel = 48f,
        carriageCount = 3,
        totalLength = 132f,
        priority = 3,
        dwellSeconds = 2.6f
    ),
    FREIGHT(
        displayName = "Freight",
        shortCode = "FRT",
        maxSpeed = 62f,
        acceleration = 22f,
        brakingDecel = 30f,
        carriageCount = 4,
        totalLength = 156f,
        priority = 1,
        dwellSeconds = 3.8f
    ),
    HEAVY_FREIGHT(
        displayName = "Heavy Freight",
        shortCode = "HVY",
        maxSpeed = 48f,
        acceleration = 14f,
        brakingDecel = 22f,
        carriageCount = 5,
        totalLength = 198f,
        priority = 0,
        dwellSeconds = 4.2f
    ),
    MAINTENANCE(
        displayName = "Maintenance",
        shortCode = "MNT",
        maxSpeed = 65f,
        acceleration = 32f,
        brakingDecel = 48f,
        carriageCount = 1,
        totalLength = 74f,
        priority = 2,
        dwellSeconds = 3.0f
    )
}

enum class TrainStatus {
    MOVING,
    BRAKING,
    WAITING_SIGNAL,
    BOARDING,
    ROUTE_MISMATCH,
    DELIVERED
}

data class TraversedSegment(
    val segmentId: String,
    val dir: TraversalDir,
    val length: Float,
    val cumulativeStart: Float
)

data class CarriagePose(
    val index: Int,
    val isLocomotive: Boolean,
    val x: Float,
    val y: Float,
    val angleRadians: Float
)

data class Train(
    val id: String,
    val serviceNumber: String,
    val name: String,
    val category: TrainCategory,
    val originNodeId: String,
    val stopStationIds: List<String>,
    val completedStationIds: Set<String> = emptySet(),
    val destinationNodeId: String,
    val currentSegmentId: String,
    val currentDir: TraversalDir,
    val distanceOnSegment: Float,
    val headTotalDistance: Float,
    val pathHistory: List<TraversedSegment>,
    val speed: Float = 0f,
    val status: TrainStatus = TrainStatus.MOVING,
    val statusReason: String = "Proceeding on schedule",
    val dwellRemaining: Float = 0f,
    val scheduledDurationSec: Float = 45f,
    val elapsedSec: Float = 0f,
    val waitTimeSec: Float = 0f,
    val intendedRouteSegments: List<String> = emptyList(),
    val isOffRoute: Boolean = false
) {
    val isDelayed: Boolean get() = elapsedSec > scheduledDurationSec
    val delaySeconds: Float get() = (elapsedSec - scheduledDurationSec).coerceAtLeast(0f)
    val nextStopStationId: String? get() = stopStationIds.firstOrNull { it !in completedStationIds }
}

data class ScheduledTrainSpawn(
    val id: String,
    val serviceNumber: String,
    val name: String,
    val category: TrainCategory,
    val entrySegmentId: String,
    val entryDir: TraversalDir,
    val stopStationIds: List<String>,
    val destinationNodeId: String,
    val spawnAtSeconds: Float,
    val targetTravelSeconds: Float
)

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

data class OperationalAlert(
    val id: String,
    val severity: AlertSeverity,
    val title: String,
    val message: String,
    val focusX: Float,
    val focusY: Float,
    val relatedTrainId: String? = null
)

data class SimulationSnapshot(
    val simTimeSeconds: Float,
    val isPaused: Boolean,
    val speedMultiplier: Float,
    val switches: Map<String, TrackSwitch>,
    val signals: Map<String, RailwaySignal>,
    val occupiedSegmentIds: Map<String, Set<String>>,
    val reservedSegmentIds: Map<String, String>,
    val trains: List<Train>,
    val trainPoses: Map<String, List<CarriagePose>>,
    val pendingSpawns: List<ScheduledTrainSpawn>,
    val alerts: List<OperationalAlert>,
    val deliveredCount: Int,
    val onTimeDeliveredCount: Int,
    val totalScore: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val safetyIncidents: Int,
    val totalDelaySeconds: Float
)
