<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Controllers\Controller;
use App\Http\Resources\V1\TagResource;
use App\Models\Tag;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use Illuminate\Validation\Rule;

class TagController extends Controller
{
    public function index(Request $request): AnonymousResourceCollection
    {
        $tags = $request->user()->tags()
            ->withCount('transactions')
            ->orderBy('name')
            ->get();

        return TagResource::collection($tags);
    }

    public function store(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'name' => [
                'required', 'string', 'max:40',
                Rule::unique('tags', 'name')->where('user_id', $request->user()->getKey()),
            ],
            'color' => ['nullable', 'string', 'max:9'],
        ]);

        $tag = $request->user()->tags()->create($validated);

        return (new TagResource($tag))->response()->setStatusCode(201);
    }

    public function update(Request $request, Tag $tag): TagResource
    {
        $validated = $request->validate([
            'name' => [
                'sometimes', 'string', 'max:40',
                Rule::unique('tags', 'name')->where('user_id', $request->user()->getKey())->ignore($tag->getKey()),
            ],
            'color' => ['nullable', 'string', 'max:9'],
        ]);

        $tag->update($validated);

        return new TagResource($tag->refresh());
    }

    public function destroy(Tag $tag): JsonResponse
    {
        $tag->delete();

        return response()->json(['message' => 'Tag deleted.']);
    }
}
