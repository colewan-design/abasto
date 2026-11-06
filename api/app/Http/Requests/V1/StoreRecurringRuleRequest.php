<?php

namespace App\Http\Requests\V1;

use App\Enums\RecurrenceFrequency;
use App\Enums\TransactionType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rules\Enum;

class StoreRecurringRuleRequest extends FormRequest
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
        $isTransfer = $this->input('type') === TransactionType::Transfer->value;

        return [
            'name' => [$required, 'string', 'max:80'],
            'type' => [$required, new Enum(TransactionType::class)],
            'account_id' => [$required, 'integer', $this->ownedAccount()],
            'transfer_account_id' => [
                $isTransfer ? 'required' : 'nullable',
                'integer', 'different:account_id', $this->ownedAccount(),
            ],
            'category_id' => ['nullable', 'integer', $this->ownedCategory()],
            'amount_minor' => [$required, 'integer', 'min:1', 'max:999999999999'],
            'merchant' => ['nullable', 'string', 'max:160'],
            'note' => ['nullable', 'string', 'max:2000'],
            'frequency' => [$required, new Enum(RecurrenceFrequency::class)],
            'interval' => ['sometimes', 'integer', 'min:1', 'max:52'],
            'by_month_day' => ['nullable', 'integer', 'min:1', 'max:31'],
            'by_weekday' => ['nullable', 'integer', 'min:0', 'max:6'],
            'start_date' => [$required, 'date_format:Y-m-d'],
            'end_date' => ['nullable', 'date_format:Y-m-d', 'after:start_date'],
            'occurrence_limit' => ['nullable', 'integer', 'min:1', 'max:600'],
            'auto_post' => ['sometimes', 'boolean'],
            'is_active' => ['sometimes', 'boolean'],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['amount' => 'amount_minor']);
    }
}
