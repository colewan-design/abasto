<?php

namespace App\Http\Requests\V1;

use App\Http\Requests\Concerns\ValidatesOwnedRecords;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;

class StoreTransferRequest extends FormRequest
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
            'from_account_id' => ['required', 'integer', $this->ownedAccount()],
            'to_account_id' => ['required', 'integer', 'different:from_account_id', $this->ownedAccount()],
            'amount_minor' => ['required', 'integer', 'min:1', 'max:999999999999'],
            'occurred_on' => ['required', 'date_format:Y-m-d'],
            'note' => ['nullable', 'string', 'max:2000'],
            'savings_goal_id' => ['nullable', 'integer', $this->ownedSavingsGoal()],
        ];
    }

    protected function prepareForValidation(): void
    {
        $this->normalizeMoney(['amount' => 'amount_minor']);
    }

    public function messages(): array
    {
        return [
            'to_account_id.different' => 'Choose a different account to transfer into.',
        ];
    }
}
