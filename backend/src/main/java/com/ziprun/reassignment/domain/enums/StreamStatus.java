package com.ziprun.reassignment.domain.enums;

public enum StreamStatus {
    PROCESSING,  // Stream in progress, use Redis
    COMPLETED,   // Done successfully, use DB
    FAILED       // Error occurred, use DB (partial events)
}
