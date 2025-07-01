package com.rr.bosses.yama;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SceneryFunction
{
    NONE("OFF"),
    SCENERY("Scenery"),
    SCENERY_AND_WALLS("Scenery and Walls");

    private final String name;

    @Override
    public String toString()
    {
        return getName();
    }
}
