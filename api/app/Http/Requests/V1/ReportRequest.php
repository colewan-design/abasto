<?php

namespace App\Http\Requests\V1;

use App\Enums\TransactionType;
use Illuminate\Contracts\Validation\ValidationRule;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Support\Carbon;
use Illuminate\Validation\Rule;

class ReportRequest extends FormRequest
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
        return [
            'month' => ['nullable', 'date_format:Y-m'],
            'from' => ['nullable', 'date_format:Y-m-d'],
            'to' => ['nullable', 'date_format:Y-m-d', 'after_or_equal:from'],
            'type' => ['nullable', Rule::in([TransactionType::Income->value, TransactionType::Expense->value])],
            'months' => ['nullable', 'integer', 'min:1', 'max:36'],
            'limit' => ['nullable', 'integer', 'min:1', 'max:50'],
        ];
    }

    /**
     * The period being reported on, defaulting to the current month.
     *
     * @return array{from: Carbon, to: Carbon}
     */
    public function period(): array
    {
        if ($this->filled('from') || $this->filled('to')) {
            $from = Carbon::parse($this->input('from', today()->startOfMonth()->toDateString()))->startOfDay();
            $to = Carbon::parse($this->input('to', today()->toDateString()))->endOfDay();

            return ['from' => $from, 'to' => $to];
        }

        $month = $this->filled('month')
            ? Carbon::createFromFormat('Y-m', $this->string('month')->toString())
            : today();

        return [
            'from' => $month->copy()->startOfMonth(),
            'to' => $month->copy()->endOfMonth(),
        ];
    }

    public function transactionType(): TransactionType
    {
        return TransactionType::tryFrom((string) $this->input('type')) ?? TransactionType::Expense;
    }
}
