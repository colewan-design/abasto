<?php

namespace App\Http\Requests\V1;

use App\Enums\CategoryType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Enum;

class StoreCategoryRequest extends FormRequest
{
    use ValidatesOwnedRecords;

    public function authorize(): bool
    {
        return $this->user() !== null;
    }

    /**
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        return [
            'name' => [
                'required', 'string', 'max:60',
                Rule::unique('categories', 'name')
                    ->where('user_id', $this->user()->getKey())
                    ->where('type', $this->input('type')),
            ],
            'type' => ['required', new Enum(CategoryType::class)],
            'category_group_id' => ['nullable', 'integer', $this->ownedBy('category_groups')],
            'new_group_name' => ['nullable', 'string', 'max:60'],
            'parent_id' => ['nullable', 'integer', $this->ownedCategory()],
            'color' => ['nullable', 'string', 'max:9'],
            'icon' => ['nullable', 'string', 'max:40'],
            'keywords' => ['nullable', 'array', 'max:60'],
            'keywords.*' => ['string', 'max:60'],
            'sort_order' => ['sometimes', 'integer', 'min:0', 'max:999'],
        ];
    }

    public function messages(): array
    {
        return [
            'name.unique' => 'You already have a category with that name.',
        ];
    }
}
