<?php

namespace App\Http\Requests\V1;

use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class StoreSavingsGoalRequest extends FormRequest
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
        $required = $this->isMethod('POST') ? 'required' : 'sometimes';

        return [
            'name' => [
                $required, 'string', 'max:80',
                Rule::unique('savings_goals', 'name')
                    ->where('user_id', $this->user()->getKey())
                    ->ignore($this->route('savings_goal')?->getKey()),
            ],
            'target_amount_minor' => [$required, 'integer', 'min:1', 'max:999999999999'],
            'target_date' => ['nullable', 'date_format:Y-m-d', 'after:today'],
            'account_id' => ['nullable', 'integer', $this->ownedAccount()],
            'color' => ['nullable', 'string', 'max:9'],
            'icon' => ['nullable', 'string', 'max:40'],
            'note' => ['nullable', 'string', 'max:2000'],
            'is_archived' => ['sometimes', 'boolean'],
            'sort_order' => ['sometimes', 'integer', 'min:0', 'max:999'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['target_amount' => 'target_amount_minor']);
    }

    public function messages(): array
    {
        return [
            'name.unique' => 'You already have a goal with that name.',
        ];
    }
}
