package br.com.gestaodireta.financial.dto;

public record FinancialReportExportFile(String filename, String contentType, byte[] content) {}
