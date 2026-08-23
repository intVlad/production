package com.example.productionmvp.model;

public enum DefectResolution {
    REWORK,
    REPLACE,
    WRITE_OFF,
    // The deviation is accepted as-is (standard QA "use as is" disposition) - no rework,
    // no replacement, no write-off task. DefectService.reportDefect has no branch for this
    // value on purpose: falling through to just saving the DefectRecord is exactly correct,
    // since a concession means nothing further happens to the part.
    CONCESSION
}
