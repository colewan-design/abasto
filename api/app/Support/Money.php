<?php

namespace App\Support;

use InvalidArgumentException;
use JsonSerializable;
use Stringable;

/**
 * An immutable amount of money held as integer minor units.
 *
 * Every monetary column in the schema is a `bigint` of minor units
 * (`*_minor`), so rounding never accumulates the way it does with floats.
 */
final class Money implements JsonSerializable, Stringable
{
    public function __construct(
        public readonly int $minor,
        public readonly string $currency,
    ) {}

    public static function ofMinor(int $minor, ?string $currency = null): self
    {
        return new self($minor, self::normalizeCurrency($currency));
    }

    /**
     * Build an amount from a major-unit value such as `1234.56` or `"1,234.56"`.
     */
    public static function ofMajor(int|float|string $major, ?string $currency = null): self
    {
        $currency = self::normalizeCurrency($currency);
        $exponent = self::exponent($currency);

        if (is_string($major)) {
            $cleaned = preg_replace('/[^0-9.\-]/', '', $major) ?? '';

            if ($cleaned === '' || $cleaned === '-' || ! is_numeric($cleaned)) {
                throw new InvalidArgumentException("Unable to read a monetary value from [{$major}].");
            }

            $major = (float) $cleaned;
        }

        return new self((int) round(((float) $major) * (10 ** $exponent)), $currency);
    }

    public static function zero(?string $currency = null): self
    {
        return new self(0, self::normalizeCurrency($currency));
    }

    public function plus(self $other): self
    {
        $this->assertSameCurrency($other);

        return new self($this->minor + $other->minor, $this->currency);
    }

    public function minus(self $other): self
    {
        $this->assertSameCurrency($other);

        return new self($this->minor - $other->minor, $this->currency);
    }

    public function absolute(): self
    {
        return new self(abs($this->minor), $this->currency);
    }

    public function negated(): self
    {
        return new self(-$this->minor, $this->currency);
    }

    public function isZero(): bool
    {
        return $this->minor === 0;
    }

    public function isNegative(): bool
    {
        return $this->minor < 0;
    }

    public function toMajor(): float
    {
        return $this->minor / (10 ** self::exponent($this->currency));
    }

    /**
     * Split an amount into `count` parts whose sum equals the original, giving
     * the leftover minor units to the earliest parts.
     *
     * @return array<int, self>
     */
    public function split(int $count): array
    {
        if ($count < 1) {
            throw new InvalidArgumentException('Cannot split money into fewer than one part.');
        }

        $base = intdiv($this->minor, $count);
        $remainder = $this->minor - ($base * $count);

        return array_map(
            fn (int $index): self => new self($base + ($index < abs($remainder) ? ($remainder <=> 0) : 0), $this->currency),
            range(0, $count - 1),
        );
    }

    /**
     * Distribute an amount across weighted parts so the parts always sum back
     * to the whole. Used when a receipt total is spread over category splits.
     *
     * @param  array<int|string, int|float>  $weights
     * @return array<int|string, self>
     */
    public function allocate(array $weights): array
    {
        $total = array_sum($weights);

        if ($total <= 0) {
            return array_map(fn () => self::zero($this->currency), $weights);
        }

        $allocated = [];
        $distributed = 0;

        foreach ($weights as $key => $weight) {
            $share = (int) floor($this->minor * ($weight / $total));
            $allocated[$key] = $share;
            $distributed += $share;
        }

        $remainder = $this->minor - $distributed;
        $keys = array_keys($allocated);

        for ($i = 0; $remainder !== 0 && $i < count($keys); $i++) {
            $step = $remainder <=> 0;
            $allocated[$keys[$i]] += $step;
            $remainder -= $step;
        }

        return array_map(fn (int $minor): self => new self($minor, $this->currency), $allocated);
    }

    public function format(bool $withSymbol = true): string
    {
        $exponent = self::exponent($this->currency);
        $symbol = $withSymbol ? self::symbol($this->currency) : '';
        $sign = $this->minor < 0 ? '-' : '';
        $formatted = number_format(abs($this->toMajor()), $exponent, '.', ',');

        return $sign.$symbol.$formatted;
    }

    public static function exponent(?string $currency = null): int
    {
        $currency = self::normalizeCurrency($currency);

        return (int) (config("budget.currencies.{$currency}.exponent") ?? 2);
    }

    public static function symbol(?string $currency = null): string
    {
        $currency = self::normalizeCurrency($currency);

        return (string) (config("budget.currencies.{$currency}.symbol") ?? $currency.' ');
    }

    public static function isSupported(string $currency): bool
    {
        return config('budget.currencies.'.strtoupper($currency)) !== null;
    }

    /**
     * @return array{minor: int, currency: string, amount: float, formatted: string}
     */
    public function jsonSerialize(): array
    {
        return [
            'minor' => $this->minor,
            'currency' => $this->currency,
            'amount' => $this->toMajor(),
            'formatted' => $this->format(),
        ];
    }

    public function __toString(): string
    {
        return $this->format();
    }

    private static function normalizeCurrency(?string $currency): string
    {
        return strtoupper($currency ?: (string) config('budget.default_currency', 'USD'));
    }

    private function assertSameCurrency(self $other): void
    {
        if ($this->currency !== $other->currency) {
            throw new InvalidArgumentException("Cannot combine {$this->currency} with {$other->currency}.");
        }
    }
}
