$version: "2"

namespace example.traits

/// A sample trait for marking operations as deprecated
@trait(selector: "*")
structure deprecatedTrait {
    /// The version when this was deprecated
    @required
    since: String

    /// Optional message explaining the deprecation
    message: String
}

/// A trait for specifying rate limiting configuration
@trait(selector: "service")
structure rateLimitTrait {
    /// Maximum requests per second
    @required
    maxRequestsPerSecond: Integer

    /// Burst capacity
    burstCapacity: Integer
}

/// A trait for adding custom metadata to shapes
@trait(selector: "*")
structure metadataTrait {
    /// Tags associated with this shape
    tags: TagList

    /// Owner of this shape
    owner: String

    /// Creation timestamp
    createdAt: Timestamp
}

list TagList {
    member: String
}

/// A trait for specifying authentication requirements
@trait(selector: "operation")
structure authRequirementTrait {
    /// Required authentication scheme
    @required
    scheme: AuthScheme

    /// Whether this operation requires admin privileges
    requiresAdmin: Boolean
}

enum AuthScheme {
    BASIC
    BEARER
    API_KEY
    OAUTH2
}

/// A simple marker trait for internal operations
@trait(selector: "operation")
structure internalTrait {}
