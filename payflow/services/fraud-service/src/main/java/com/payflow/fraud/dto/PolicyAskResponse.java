package com.payflow.fraud.dto;

import java.util.List;

public record PolicyAskResponse(String answer, List<String> sources) {
}
