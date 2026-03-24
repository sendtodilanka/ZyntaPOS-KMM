import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

export interface ExchangeRateDto {
  id: string;
  sourceCurrency: string;
  targetCurrency: string;
  rate: number;
  effectiveDate: string;
  expiresAt: string | null;
  source: string;
  createdAt: string;
  updatedAt: string;
}

export interface ExchangeRatesResponse {
  total: number;
  rates: ExchangeRateDto[];
}

export interface UpsertExchangeRateRequest {
  sourceCurrency: string;
  targetCurrency: string;
  rate: number;
  source?: string;
}

const QUERY_KEY = 'exchange-rates';

export function useExchangeRates() {
  return useQuery({
    queryKey: [QUERY_KEY],
    queryFn: () =>
      apiClient.get('admin/exchange-rates').json<ExchangeRatesResponse>(),
  });
}

export function useUpsertExchangeRate() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpsertExchangeRateRequest) =>
      apiClient.put('admin/exchange-rates', { json: data }).json<ExchangeRateDto>(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [QUERY_KEY] });
    },
  });
}
