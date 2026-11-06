package com.budgettracker.feature.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgettracker.core.money.Currency
import com.budgettracker.core.money.Money
import com.budgettracker.core.network.ApiException
import com.budgettracker.core.network.Outcome
import com.budgettracker.core.time.Dates
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.SavingsGoal
import com.budgettracker.data.remote.request.ContributionRequest
import com.budgettracker.data.remote.request.SavingsGoalRequest
import com.budgettracker.data.repository.AuthRepository
import com.budgettracker.data.repository.ReferenceRepository
import com.budgettracker.data.repository.SavingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class GoalForm(
    val id: Long? = null,
    val name: String = "",
    val target: String = "",
    val targetDate: LocalDate? = null,
    val accountId: Long? = null,
    val note: String = "",
    val saving: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
) {
    val isEditing: Boolean get() = id != null
    val canSave: Boolean get() = name.isNotBlank() && target.isNotBlank() && !saving
}

/**
 * Adding to a goal. `fromAccountId` is what makes it move real money; without
 * it the goal is credited on its own, for cash set aside outside the app.
 */
data class ContributionForm(
    val goal: SavingsGoal,
    val amount: String = "",
    val date: LocalDate = Dates.today(),
    val fromAccountId: Long? = null,
    val moveMoney: Boolean = true,
    val note: String = "",
    val saving: Boolean = false,
)

data class SavingsUiState(
    val loading: Boolean = true,
    val error: ApiException? = null,
    val message: String? = null,
    val goals: List<SavingsGoal> = emptyList(),
    val currency: Currency = Currency.Default,
    val accounts: List<Account> = emptyList(),
    val form: GoalForm? = null,
    val contributing: ContributionForm? = null,
) {
    val active: List<SavingsGoal> get() = goals.filterNot { it.isAchieved }
    val achieved: List<SavingsGoal> get() = goals.filter { it.isAchieved }
}

/** Savings goals and the money put towards them. */
@HiltViewModel
class SavingsViewModel @Inject constructor(
    private val savings: SavingsRepository,
    private val reference: ReferenceRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SavingsUiState(currency = auth.currency))
    val state: StateFlow<SavingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            reference.ensureLoaded()

            _state.update {
                it.copy(accounts = reference.accounts.value.filterNot { a -> a.isArchived })
            }
        }

        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.goals.isEmpty(), error = null) }

            when (val outcome = savings.list()) {
                is Outcome.Ok -> _state.update { it.copy(loading = false, goals = outcome.value) }
                is Outcome.Err -> _state.update { it.copy(loading = false, error = outcome.error) }
            }
        }
    }

    /* --------------------------------------------------------------- form */

    fun addGoal() = _state.update { it.copy(form = GoalForm()) }

    fun editGoal(goal: SavingsGoal) = _state.update { current ->
        current.copy(
            form = GoalForm(
                id = goal.id,
                name = goal.name,
                target = Money.majorInput(goal.targetAmountMinor, current.currency.exponent),
                targetDate = Dates.parse(goal.targetDate),
                accountId = goal.accountId,
                note = goal.note.orEmpty(),
            ),
        )
    }

    fun closeForm() = _state.update { it.copy(form = null) }

    fun editForm(transform: (GoalForm) -> GoalForm) = _state.update { current ->
        current.copy(form = current.form?.let(transform))
    }

    fun saveForm() {
        val current = _state.value
        val form = current.form ?: return
        val targetMinor = Money.parse(form.target, current.currency.exponent)

        if (targetMinor == null || targetMinor <= 0L) {
            editForm { it.copy(fieldErrors = mapOf("target_amount_minor" to "Enter a target.")) }

            return
        }

        viewModelScope.launch {
            editForm { it.copy(saving = true, fieldErrors = emptyMap()) }

            val request = SavingsGoalRequest(
                name = form.name.trim(),
                targetAmountMinor = targetMinor,
                targetDate = form.targetDate?.let(Dates::wire),
                accountId = form.accountId,
                note = form.note.trim().takeIf { it.isNotBlank() },
            )

            val outcome = when (val id = form.id) {
                null -> savings.create(request)
                else -> savings.update(id, request)
            }

            when (outcome) {
                is Outcome.Ok -> {
                    _state.update { it.copy(form = null, message = "${outcome.value.name} saved.") }
                    load()
                }

                is Outcome.Err -> editForm {
                    it.copy(
                        saving = false,
                        fieldErrors = outcome.error.errors.mapValues { (_, m) -> m.first() },
                    )
                }
            }
        }
    }

    /* -------------------------------------------------------- contributing */

    fun startContribution(goal: SavingsGoal) = _state.update { current ->
        current.copy(
            contributing = ContributionForm(
                goal = goal,
                amount = goal.suggestedMonthlyMinor
                    ?.let { Money.majorInput(it, current.currency.exponent) }
                    .orEmpty(),
                fromAccountId = reference.defaultAccount()?.id,
            ),
        )
    }

    fun editContribution(transform: (ContributionForm) -> ContributionForm) =
        _state.update { current -> current.copy(contributing = current.contributing?.let(transform)) }

    fun cancelContribution() = _state.update { it.copy(contributing = null) }

    fun saveContribution() {
        val current = _state.value
        val form = current.contributing ?: return
        val amountMinor = Money.parse(form.amount, current.currency.exponent)

        if (amountMinor == null || amountMinor <= 0L) {
            _state.update { it.copy(message = "Enter an amount to put aside.") }

            return
        }

        viewModelScope.launch {
            editContribution { it.copy(saving = true) }

            val outcome = savings.contribute(
                id = form.goal.id,
                request = ContributionRequest(
                    amountMinor = amountMinor,
                    contributedOn = Dates.wire(form.date),
                    fromAccountId = form.fromAccountId.takeIf { form.moveMoney },
                    note = form.note.trim().takeIf { it.isNotBlank() },
                ),
            )

            when (outcome) {
                is Outcome.Ok -> {
                    _state.update {
                        it.copy(
                            contributing = null,
                            message = outcome.value.message ?: "Added to ${form.goal.name}.",
                        )
                    }

                    load()
                }

                is Outcome.Err -> {
                    editContribution { it.copy(saving = false) }
                    _state.update { it.copy(message = outcome.error.message) }
                }
            }
        }
    }

    fun archive(goal: SavingsGoal) {
        viewModelScope.launch {
            when (savings.update(goal.id, SavingsGoalRequest(isArchived = true))) {
                is Outcome.Ok -> {
                    _state.update { it.copy(message = "${goal.name} archived.") }
                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = "That could not be archived.") }
            }
        }
    }

    fun delete(goal: SavingsGoal) {
        viewModelScope.launch {
            when (val outcome = savings.delete(goal.id)) {
                is Outcome.Ok -> {
                    _state.update { it.copy(message = "${goal.name} removed.") }
                    load()
                }

                is Outcome.Err -> _state.update { it.copy(message = outcome.error.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
