<?php

namespace App\Http\Requests\V1;

use App\Enums\BillRecurrence;
use App\Enums\CategoryType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rules\Enum;

class StoreBillRequest extends FormRequest
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
            'name' => ['required', 'string', 'max:80'],
            'payee' => ['nullable', 'string', 'max:120'],
            'amount_minor' => ['required', 'integer', 'min:1', 'max:999999999999'],
            'due_date' => ['required', 'date_format:Y-m-d'],
            'recurrence' => ['required', new Enum(BillRecurrence::class)],
            'recurrence_interval' => ['sometimes', 'integer', 'min:1', 'max:24'],
            'reminder_days_before' => ['sometimes', 'integer', 'min:0', 'max:60'],
            'auto_pay' => ['sometimes', 'boolean'],
            'account_id' => ['nullable', 'integer', $this->ownedAccount()],
            'category_id' => ['nullable', 'integer', $this->ownedCategory(CategoryType::Expense->value)],
            'note' => ['nullable', 'string', 'max:2000'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['amount' => 'amount_minor']);
    }
}
