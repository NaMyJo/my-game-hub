package com.mygamehub.game.dto;

import java.util.List;

public record GameAccountReorderRequest(
        List<Long> gameIds
) {
}