<?php

namespace Tests\Unit;

use App\Support\Money;
use InvalidArgumentException;
use PHPUnit\Framework\Attributes\TestWith;
use Tests\TestCase;

class MoneyTest extends TestCase
{
    #[TestWith(['1234.56', 123456], 'plain decimal')]
    #[TestWith(['1,234.56', 123456], 'thousands separator')]
    #[TestWith(['₱1,234.56', 123456], 'currency symbol')]
    #[TestWith(['0.05', 5], 'cents only')]
    #[TestWith(['1234', 123400], 'no decimal part')]
    #[TestWith(['-45.50', -4550], 'negative')]
    public function test_it_reads_major_units_into_minor_units(string $input, int $expected): void
    {
        $this->assertSame($expected, Money::ofMajor($input, 'PHP')->minor);
    }

    public function test_it_rejects_a_value_that_is_not_monetary(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Money::ofMajor('not a number', 'PHP');
    }

    public function test_it_uses_the_currency_exponent_for_zero_decimal_currencies(): void
    {
        $this->assertSame(1234, Money::ofMajor('1234', 'JPY')->minor);
        $this->assertSame(123400, Money::ofMajor('1234', 'USD')->minor);
    }

    /**
     * The receipt split flow depends on this: a total spread across categories
     * has to add back up to the amount that was actually charged.
     */
    public function test_allocating_a_total_across_weights_preserves_the_total(): void
    {
        $total = Money::ofMinor(100_00, 'PHP');

        $parts = $total->allocate(['groceries' => 828_00, 'household' => 626_00]);

        // 82800 / 145400 of 10000 is 5694.63, and the leftover minor unit goes
        // to the first part so the two shares still sum to exactly 10000.
        $this->assertSame(10000, array_sum(array_map(fn (Money $part): int => $part->minor, $parts)));
        $this->assertSame(5695, $parts['groceries']->minor);
        $this->assertSame(4305, $parts['household']->minor);
    }

    public function test_allocating_an_indivisible_amount_gives_the_remainder_to_the_first_parts(): void
    {
        $parts = Money::ofMinor(10, 'PHP')->allocate([1, 1, 1]);

        $this->assertSame([4, 3, 3], array_map(fn (Money $part): int => $part->minor, $parts));
        $this->assertSame(10, array_sum(array_map(fn (Money $part): int => $part->minor, $parts)));
    }

    public function test_allocating_with_no_weight_returns_zeroes(): void
    {
        $parts = Money::ofMinor(500, 'PHP')->allocate(['a' => 0, 'b' => 0]);

        $this->assertSame([0, 0], array_values(array_map(fn (Money $part): int => $part->minor, $parts)));
    }

    public function test_splitting_evenly_preserves_the_total(): void
    {
        $parts = Money::ofMinor(100, 'PHP')->split(3);

        $this->assertSame([34, 33, 33], array_map(fn (Money $part): int => $part->minor, $parts));
    }

    public function test_it_formats_with_the_currency_symbol_and_exponent(): void
    {
        $this->assertSame('₱1,234.56', Money::ofMinor(123456, 'PHP')->format());
        $this->assertSame('-₱1,234.56', Money::ofMinor(-123456, 'PHP')->format());
        $this->assertSame('¥1,234', Money::ofMinor(1234, 'JPY')->format());
        $this->assertSame('1,234.56', Money::ofMinor(123456, 'PHP')->format(withSymbol: false));
    }

    public function test_it_refuses_to_combine_different_currencies(): void
    {
        $this->expectException(InvalidArgumentException::class);

        Money::ofMinor(100, 'PHP')->plus(Money::ofMinor(100, 'USD'));
    }

    public function test_it_adds_and_subtracts_within_one_currency(): void
    {
        $sum = Money::ofMinor(100_00, 'PHP')->plus(Money::ofMinor(50_00, 'PHP'));

        $this->assertSame(15000, $sum->minor);
        $this->assertSame(5000, $sum->minus(Money::ofMinor(100_00, 'PHP'))->minor);
    }
}
