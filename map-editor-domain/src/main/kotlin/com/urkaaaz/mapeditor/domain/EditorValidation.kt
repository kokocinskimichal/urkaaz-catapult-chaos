package com.urkaaaz.mapeditor.domain

import com.urkaaaz.campaign.MapDefinitionValidator
import com.urkaaaz.campaign.MapValidationResult
import com.urkaaaz.contracts.MapDefinition

object EditorValidation {
    fun validateForEditor(map: MapDefinition): MapValidationResult =
        MapValidationResult(
            MapDefinitionValidator.validate(map).errors.filterNot { it.path.startsWith("objects[") },
        )
}
