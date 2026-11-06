<?php

namespace App\Http\Requests\V1;

use App\Enums\CategoryType;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Enum;

class StoreCategoryGroupRequest extends FormRequest
{
    public function authorize(): bool
    {
        return $this->user() !== null;
    }

    /**
     * @return array<string, ValidationRule|array<mixed>|string>
     */
    public function rules(): array
    {
        $group = $this->route('category_group');

        return [
            'name' => [
                $this->isMethod('POST') ? 'required' : 'sometimes', 'string', 'max:60',
                Rule::unique('category_groups', 'name')
                    ->where('user_id', $this->user()->getKey())
                    ->where('type', $this->input('type', $group?->type->value))
                    ->ignore($group?->getKey()),
            ],
            'type' => [$this->isMethod('POST') ? 'required' : 'sometimes', new Enum(CategoryType::class)],
            'color' => ['nullable', 'string', 'max:9'],
            'icon' => ['nullable', 'string', 'max:40'],
            'sort_order' => ['sometimes', 'integer', 'min:0', 'max:999'],
        ];
    }
}
