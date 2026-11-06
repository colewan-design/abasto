<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Requests\V1\StoreCategoryGroupRequest;
use App\Http\Resources\V1\CategoryGroupResource;
use App\Models\CategoryGroup;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;

class CategoryGroupController extends Controller
{
    public function index(Request $request): AnonymousResourceCollection
    {
        $groups = $request->user()->categoryGroups()
            ->with(['categories' => fn ($query) => $query->active()->orderBy('sort_order')->orderBy('name')])
            ->when($request->filled('type'), fn ($query) => $query->ofType($request->string('type')->toString()))
            ->orderBy('type')
            ->orderBy('sort_order')
            ->orderBy('name')
            ->get();

        return CategoryGroupResource::collection($groups);
    }

    public function store(StoreCategoryGroupRequest $request): JsonResponse
    {
        $group = $request->user()->categoryGroups()->create($request->validated());

        return (new CategoryGroupResource($group))->response()->setStatusCode(201);
    }

    public function show(CategoryGroup $categoryGroup): CategoryGroupResource
    {
        return new CategoryGroupResource($categoryGroup->load('categories'));
    }

    public function update(StoreCategoryGroupRequest $request, CategoryGroup $categoryGroup): CategoryGroupResource
    {
        $categoryGroup->update($request->validated());

        return new CategoryGroupResource($categoryGroup->refresh()->load('categories'));
    }

    /**
     * Deleting a group leaves its categories in place, just ungrouped.
     */
    public function destroy(CategoryGroup $categoryGroup): JsonResponse
    {
        $categoryGroup->delete();

        return response()->json(['message' => 'Group deleted. Its categories were kept.']);
    }
}
