package com.zyntasolutions.zyntapos.domain.usecase.fakes

import com.zyntasolutions.zyntapos.core.result.DatabaseException
import com.zyntasolutions.zyntapos.core.result.Result
import com.zyntasolutions.zyntapos.core.result.ValidationException
import com.zyntasolutions.zyntapos.domain.model.TaxGroup
import com.zyntasolutions.zyntapos.domain.model.UnitOfMeasure
import com.zyntasolutions.zyntapos.domain.repository.TaxGroupRepository
import com.zyntasolutions.zyntapos.domain.repository.UnitGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

// ─────────────────────────────────────────────────────────────────────────────
// Test Fixtures — Sprint 19
// ─────────────────────────────────────────────────────────────────────────────

/** Builds a [TaxGroup] with sensible test defaults. */
fun buildTaxGroup(
    id: String = "tax-01",
    name: String = "Standard VAT",
    rate: Double = 15.0,
    isInclusive: Boolean = false,
    isActive: Boolean = true,
) = TaxGroup(id = id, name = name, rate = rate, isInclusive = isInclusive, isActive = isActive)

/** Builds a [UnitOfMeasure] with sensible test defaults. */
fun buildUnit(
    id: String = "unit-01",
    name: String = "Kilogram",
    abbreviation: String = "kg",
    isBaseUnit: Boolean = true,
    conversionRate: Double = 1.0,
) = UnitOfMeasure(
    id = id, name = name, abbreviation = abbreviation,
    isBaseUnit = isBaseUnit, conversionRate = conversionRate,
)

// ─────────────────────────────────────────────────────────────────────────────
// FakeTaxGroupRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory implementation of [TaxGroupRepository] for unit tests.
 *
 * Mimics the data-layer's name-uniqueness constraint and basic CRUD behaviour.
 */
class FakeTaxGroupRepository : TaxGroupRepository {

    val taxGroups = mutableListOf<TaxGroup>()
    private val _flow = MutableStateFlow<List<TaxGroup>>(emptyList())

    override fun getAll(): Flow<List<TaxGroup>> = _flow

    override suspend fun getById(id: String): Result<TaxGroup> =
        taxGroups.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("TaxGroup '$id' not found"))

    override suspend fun insert(taxGroup: TaxGroup): Result<Unit> {
        val nameConflict = taxGroups.any {
            it.name.equals(taxGroup.name, ignoreCase = true) && it.id != taxGroup.id
        }
        if (nameConflict) {
            return Result.Error(
                ValidationException(
                    "Tax group name '${taxGroup.name}' already exists.",
                    field = "name",
                    rule = "NAME_DUPLICATE",
                )
            )
        }
        taxGroups.add(taxGroup)
        _flow.value = taxGroups.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(taxGroup: TaxGroup): Result<Unit> {
        val index = taxGroups.indexOfFirst { it.id == taxGroup.id }
        if (index == -1) return Result.Error(DatabaseException("TaxGroup not found"))
        taxGroups[index] = taxGroup
        _flow.value = taxGroups.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        taxGroups.removeAll { it.id == id }
        _flow.value = taxGroups.toList()
        return Result.Success(Unit)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FakeUnitGroupRepository
// ─────────────────────────────────────────────────────────────────────────────

/**
 * In-memory implementation of [UnitGroupRepository] for unit tests.
 *
 * Enforces the base-unit uniqueness constraint within each unit group.
 */
class FakeUnitGroupRepository : UnitGroupRepository {

    val units = mutableListOf<UnitOfMeasure>()
    private val _flow = MutableStateFlow<List<UnitOfMeasure>>(emptyList())
    var shouldFailDelete: Boolean = false

    override fun getAll(): Flow<List<UnitOfMeasure>> = _flow

    override suspend fun getById(id: String): Result<UnitOfMeasure> =
        units.firstOrNull { it.id == id }
            ?.let { Result.Success(it) }
            ?: Result.Error(DatabaseException("Unit '$id' not found"))

    override suspend fun insert(unit: UnitOfMeasure): Result<Unit> {
        units.add(unit)
        _flow.value = units.toList()
        return Result.Success(Unit)
    }

    override suspend fun update(unit: UnitOfMeasure): Result<Unit> {
        val index = units.indexOfFirst { it.id == unit.id }
        if (index == -1) return Result.Error(DatabaseException("Unit not found"))
        units[index] = unit
        _flow.value = units.toList()
        return Result.Success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        if (shouldFailDelete) {
            return Result.Error(
                ValidationException("Cannot delete unit in use.", field = "unitId", rule = "IN_USE")
            )
        }
        units.removeAll { it.id == id }
        _flow.value = units.toList()
        return Result.Success(Unit)
    }
}
