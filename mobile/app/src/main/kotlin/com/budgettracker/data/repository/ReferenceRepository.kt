package com.budgettracker.data.repository

import com.budgettracker.core.network.Outcome
import com.budgettracker.core.network.apiCall
import com.budgettracker.core.network.discard
import com.budgettracker.data.model.Account
import com.budgettracker.data.model.Category
import com.budgettracker.data.model.CategoryGroup
import com.budgettracker.data.model.CategorySuggestion
import com.budgettracker.data.model.CategoryType
import com.budgettracker.data.model.Tag
import com.budgettracker.data.remote.BudgetApi
import com.budgettracker.data.remote.request.CategoryRequest
import com.budgettracker.data.remote.request.SuggestCategoryRequest
import com.budgettracker.data.remote.request.TagRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The lists every form needs — accounts, categories, tags.
 *
 * They change rarely and are read constantly (a composer, a filter sheet, a
 * receipt review all need them at once), so they are fetched once per session
 * and shared. [ensureLoaded] is safe to call from several screens opening at
 * the same time: the mutex means one request, not four.
 */
@Singleton
class ReferenceRepository @Inject constructor(
    private val api: BudgetApi,
) {

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    private val _groups = MutableStateFlow<List<CategoryGroup>>(emptyList())
    private val _tags = MutableStateFlow<List<Tag>>(emptyList())

    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()
    val groups: StateFlow<List<CategoryGroup>> = _groups.asStateFlow()
    val tags: StateFlow<List<Tag>> = _tags.asStateFlow()

    private val loadLock = Mutex()
    private var loaded = false

    fun expenseCategories(): List<Category> =
        _categories.value.filter { it.type == CategoryType.Expense }

    fun incomeCategories(): List<Category> =
        _categories.value.filter { it.type == CategoryType.Income }

    fun categoriesOfType(type: CategoryType): List<Category> =
        _categories.value.filter { it.type == type }

    fun category(id: Long?): Category? =
        id?.let { wanted -> _categories.value.firstOrNull { it.id == wanted } }

    fun account(id: Long?): Account? =
        id?.let { wanted -> _accounts.value.firstOrNull { it.id == wanted } }

    /** The account a new entry should default to: the first one in order. */
    fun defaultAccount(): Account? = _accounts.value.firstOrNull { !it.isArchived }

    suspend fun ensureLoaded(): Outcome<Unit> = loadLock.withLock {
        if (loaded) return@withLock Outcome.Ok(Unit)

        refreshInternal().also { if (it is Outcome.Ok) loaded = true }
    }

    suspend fun refresh(): Outcome<Unit> = loadLock.withLock {
        refreshInternal().also { if (it is Outcome.Ok) loaded = true }
    }

    /** Drops the cache so the next screen reloads it — used after sign-out. */
    fun clear() {
        loaded = false
        _accounts.value = emptyList()
        _categories.value = emptyList()
        _groups.value = emptyList()
        _tags.value = emptyList()
    }

    suspend fun createCategory(request: CategoryRequest): Outcome<Category> =
        apiCall { api.createCategory(request).data }
            .also { outcome ->
                if (outcome is Outcome.Ok) {
                    _categories.value = (_categories.value + outcome.value).sortedWith(categoryOrder)
                }
            }

    suspend fun updateCategory(id: Long, request: CategoryRequest): Outcome<Category> =
        apiCall { api.updateCategory(id, request).data }
            .also { outcome ->
                if (outcome is Outcome.Ok) {
                    _categories.value = _categories.value
                        .map { if (it.id == id) outcome.value else it }
                        .sortedWith(categoryOrder)
                }
            }

    suspend fun deleteCategory(id: Long, reassignTo: Long? = null): Outcome<Unit> =
        apiCall { api.deleteCategory(id, reassignTo) }
            .discard()
            .also { outcome ->
                if (outcome is Outcome.Ok) {
                    _categories.value = _categories.value.filterNot { it.id == id }
                }
            }

    suspend fun createTag(name: String): Outcome<Tag> =
        apiCall { api.createTag(TagRequest(name = name.trim())).data }
            .also { outcome ->
                if (outcome is Outcome.Ok) _tags.value = _tags.value + outcome.value
            }

    /**
     * What the server thinks this merchant belongs under. Used as the composer
     * is typed in, so a wrong guess is cheap and never blocks saving.
     */
    suspend fun suggestCategory(
        merchant: String? = null,
        description: String? = null,
        type: CategoryType = CategoryType.Expense,
    ): Outcome<CategorySuggestion> = apiCall {
        api.suggestCategory(
            SuggestCategoryRequest(
                merchant = merchant?.trim()?.takeIf { it.isNotBlank() },
                description = description?.trim()?.takeIf { it.isNotBlank() },
                type = type.wire,
            ),
        ).data
    }

    private suspend fun refreshInternal(): Outcome<Unit> {
        val accounts = apiCall { api.accounts() }
        if (accounts is Outcome.Err) return accounts

        val categories = apiCall { api.categories() }
        if (categories is Outcome.Err) return categories

        val groups = apiCall { api.categoryGroups() }
        if (groups is Outcome.Err) return groups

        val tags = apiCall { api.tags() }
        if (tags is Outcome.Err) return tags

        _accounts.value = (accounts as Outcome.Ok).value.data
        _categories.value = (categories as Outcome.Ok).value.data.sortedWith(categoryOrder)
        _groups.value = (groups as Outcome.Ok).value.data
        _tags.value = (tags as Outcome.Ok).value.data

        return Outcome.Ok(Unit)
    }

    /** Grouped first, then by the order the account arranged them in. */
    private val categoryOrder = compareBy<Category>(
        { it.group?.name ?: "" },
        { it.sortOrder },
        { it.name },
    )
}
