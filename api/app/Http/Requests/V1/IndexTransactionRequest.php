<?php

namespace App\Http\Requests\V1;

use App\Enums\TransactionType;
use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class IndexTransactionRequest extends FormRequest
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
        $types = array_map(fn (TransactionType $type): string => $type->value, TransactionType::cases());

        return [
            'q' => ['nullable', 'string', 'max:120'],
            'type' => ['nullable', Rule::in([...$types, 'all', 'cash_flow'])],
            'account_id' => ['nullable', 'array'],
            'account_id.*' => ['integer', $this->ownedAccount()],
            'category_id' => ['nullable', 'array'],
            'category_id.*' => ['integer', $this->ownedBy('categories')],
            'tag_id' => ['nullable', 'array'],
            'tag_id.*' => ['integer', $this->ownedTag()],
            'from' => ['nullable', 'date_format:Y-m-d'],
            'to' => ['nullable', 'date_format:Y-m-d', 'after_or_equal:from'],
            'min_amount_minor' => ['nullable', 'integer', 'min:0'],
            'max_amount_minor' => ['nullable', 'integer', 'min:0', 'gte:min_amount_minor'],
            'has_receipt' => ['nullable', 'boolean'],
            'uncategorized' => ['nullable', 'boolean'],
            'savings_goal_id' => ['nullable', 'integer', $this->ownedSavingsGoal()],
            'bill_id' => ['nullable', 'integer', $this->ownedBill()],
            'recurring_rule_id' => ['nullable', 'integer', $this->ownedBy('recurring_rules')],
            'collapse_transfers' => ['nullable', 'boolean'],
            'sort' => ['nullable', Rule::in(['occurred_on', 'amount', 'merchant', 'created_at'])],
            'direction' => ['nullable', Rule::in(['asc', 'desc'])],
            'per_page' => ['nullable', 'integer', 'min:1', 'max:200'],
        ];
    }

    /**
     * Accept comma separated ids and single values for the array filters so
     * the same endpoint works from a URL bar and from a typed client.
     */
    protected function prepareForValidation(): void
    {
        foreach (['account_id', 'category_id', 'tag_id'] as $field) {
            $value = $this->input($field);

            if ($value === null || is_array($value)) {
                continue;
            }

            $this->merge([
                $field => collect(explode(',', (string) $value))
                    ->map(fn (string $id): string => trim($id))
                    ->filter()
                    ->values()
                    ->all(),
            ]);
        }

        $this->normalizeMoney([
            'min_amount' => 'min_amount_minor',
            'max_amount' => 'max_amount_minor',
        ]);
    }

    /**
     * @return array<string, mixed>
     */
    public function filters(): array
    {
        return array_filter(
            $this->safe()->except('per_page'),
            fn ($value): bool => $value !== null && $value !== '' && $value !== [],
        );
    }
}
