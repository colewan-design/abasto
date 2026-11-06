<?php

namespace App\Http\Controllers\Api\V1;

use App\Enums\CategoryType;
use App\Http\Controllers\Controller;
use App\Http\Requests\V1\StoreCategoryRequest;
use App\Http\Requests\V1\UpdateCategoryRequest;
use App\Http\Resources\V1\CategoryResource;
use App\Models\Category;
use App\Services\CategorySuggester;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class CategoryController extends Controller
{
    public function __construct(
        private readonly CategorySuggester $suggester,
    ) {}

    public function index(Request $request): AnonymousResourceCollection
    {
        $categories = $request->user()->categories()
            ->with('group')
            ->when($request->filled('type'), fn ($query) => $query->ofType($request->string('type')->toString()))
            ->when(! $request->boolean('include_archived'), fn ($query) => $query->active())
            ->when($request->boolean('with_counts'), fn ($query) => $query->withCount('transactions'))
            ->orderBy('type')
            ->orderBy('sort_order')
            ->orderBy('name')
            ->get();

        return CategoryResource::collection($categories);
    }

    public function store(StoreCategoryRequest $request): JsonResponse
    {
        $user = $request->user();
        $data = $request->validated();

        $category = DB::transaction(function () use ($user, $data): Category {
            $groupId = $data['category_group_id'] ?? null;

            // A new category can bring its own group along, which is what the
            // "suggest a new category" flow on the receipt screen posts.
            if ($groupId === null && filled($data['new_group_name'] ?? null)) {
                $groupId = $user->categoryGroups()->firstOrCreate(
                    [
                        'type' => $data['type'],
                        'name' => $data['new_group_name'],
                    ],
                    ['color' => $data['color'] ?? null, 'icon' => $data['icon'] ?? null],
                )->getKey();
            }

            return $user->categories()->create([
                'name' => $data['name'],
                'type' => $data['type'],
                'category_group_id' => $groupId,
                'parent_id' => $data['parent_id'] ?? null,
                'color' => $data['color'] ?? null,
                'icon' => $data['icon'] ?? null,
                'keywords' => $data['keywords'] ?? [],
                'sort_order' => $data['sort_order'] ?? 0,
            ]);
        });

        return (new CategoryResource($category->load('group')))->response()->setStatusCode(201);
    }

    public function show(Category $category): CategoryResource
    {
        return new CategoryResource($category->load('group')->loadCount('transactions'));
    }

    public function update(UpdateCategoryRequest $request, Category $category): CategoryResource
    {
        $data = $request->validated();

        $category->update(collect($data)->except('archived')->all());

        if ($request->has('archived')) {
            $category->forceFill(['archived_at' => $request->boolean('archived') ? now() : null])->save();
        }

        return new CategoryResource($category->refresh()->load('group'));
    }

    /**
     * Categories in use are archived instead of deleted, unless the caller
     * supplies a replacement to move their transactions to.
     */
    public function destroy(Request $request, Category $category): JsonResponse
    {
        $request->validate([
            'reassign_to' => ['nullable', 'integer', 'different:category'],
        ]);

        $inUse = $category->transactions()->exists();
        $replacement = $request->filled('reassign_to')
            ? $request->user()->categories()->whereKey($request->integer('reassign_to'))->first()
            : null;

        if ($request->filled('reassign_to') && $replacement === null) {
            throw ValidationException::withMessages([
                'reassign_to' => 'That category does not exist.',
            ]);
        }

        if ($inUse && $replacement === null) {
            $category->forceFill(['archived_at' => now()])->save();

            return response()->json([
                'message' => 'This category is used by existing transactions, so it was archived instead of deleted.',
                'archived' => true,
                'category' => new CategoryResource($category->refresh()),
            ]);
        }

        DB::transaction(function () use ($category, $replacement): void {
            if ($replacement !== null) {
                $category->transactions()->update(['category_id' => $replacement->getKey()]);
                DB::table('transaction_splits')
                    ->where('category_id', $category->getKey())
                    ->update(['category_id' => $replacement->getKey()]);
            }

            $category->delete();
        });

        return response()->json(['message' => 'Category deleted.']);
    }

    /**
     * Suggest a category for a merchant, used while typing a transaction and
     * on the receipt review screen.
     */
    public function suggest(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'merchant' => ['nullable', 'string', 'max:160'],
            'description' => ['nullable', 'string', 'max:200'],
            'type' => ['nullable', 'string', 'in:income,expense'],
        ]);

        $user = $request->user();
        $type = CategoryType::tryFrom($validated['type'] ?? 'expense') ?? CategoryType::Expense;

        $suggestion = filled($validated['merchant'] ?? null)
            ? $this->suggester->suggestForMerchant($user, $validated['merchant'], $type)
            : $this->suggester->suggestForItem($user, (string) ($validated['description'] ?? ''), $type);

        $category = $suggestion['category_id'] === null
            ? null
            : $user->categories()->with('group')->find($suggestion['category_id']);

        return response()->json([
            'category' => $category === null ? null : new CategoryResource($category),
            'confidence' => $suggestion['confidence'],
            'source' => $suggestion['source'],
            'proposal' => $category === null
                ? $this->suggester->proposeCategory($validated['merchant'] ?? null, $type)
                : null,
        ]);
    }
}
