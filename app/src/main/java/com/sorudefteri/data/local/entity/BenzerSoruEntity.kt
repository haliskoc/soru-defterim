package com.sorudefteri.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sorudefteri.domain.model.BenzerSoru
import kotlinx.serialization.json.Json

/**
 * Bir soruya bağlı kalıcı benzer soru kaydı.
 * Manuel JSON yapıştırma veya AI ile türetilen benzer sorular soruya özel saklanır.
 */
@Entity(
    tableName = "benzer_sorular",
    foreignKeys = [
        ForeignKey(
            entity = SoruEntity::class,
            parentColumns = ["id"],
            childColumns = ["soruId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["soruId"]), Index(value = ["createdAt"])]
)
data class BenzerSoruEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val soruId: Long,
    val questionText: String,
    val optionsJson: String = "{}",
    val correctAnswer: String = "A",
    val solution: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): BenzerSoru {
        val options: Map<String, String> = try {
            Json.decodeFromString<Map<String, String>>(optionsJson)
        } catch (_: Exception) {
            emptyMap()
        }
        return BenzerSoru(
            id = id,
            soruId = soruId,
            questionText = questionText,
            options = options,
            correctAnswer = correctAnswer,
            solution = solution,
            createdAt = createdAt
        )
    }
}

fun BenzerSoru.toEntity(): BenzerSoruEntity {
    val json = try {
        Json.encodeToString(options)
    } catch (_: Exception) {
        "{}"
    }
    return BenzerSoruEntity(
        id = id,
        soruId = soruId,
        questionText = questionText,
        optionsJson = json,
        correctAnswer = correctAnswer,
        solution = solution,
        createdAt = createdAt
    )
}
