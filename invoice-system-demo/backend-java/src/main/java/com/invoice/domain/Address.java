package com.invoice.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地址信息
 * 
 * 与 Python Address 模型功能完全等价
 * 表示地理位置信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    /**
     * 街道地址
     */
    @JsonProperty("street")
    private String street;

    /**
     * 城市
     */
    @JsonProperty("city")
    private String city;

    /**
     * 省份/州
     */
    @JsonProperty("state")
    private String state;

    /**
     * 邮政编码
     */
    @JsonProperty("postal_code")
    private String postalCode;

    /**
     * 国家代码
     */
    @JsonProperty("country")
    @Builder.Default
    private String country = "CN";

    /**
     * 区/县
     */
    @JsonProperty("district")
    private String district;

    /**
     * 详细地址
     */
    @JsonProperty("detail")
    private String detail;

    /**
     * 地址类型
     */
    @JsonProperty("address_type")
    private String addressType;

    /**
     * 纬度
     */
    @JsonProperty("latitude")
    private Double latitude;

    /**
     * 经度
     */
    @JsonProperty("longitude")
    private Double longitude;

    /**
     * 获取完整地址字符串
     * 
     * 按照中国地址格式组装完整地址
     * 
     * @return 完整地址字符串
     */
    public String getFullAddress() {
        StringBuilder fullAddress = new StringBuilder();
        
        // 按照：国家 + 省份 + 城市 + 区县 + 街道 + 详细地址 的顺序
        if (country != null && !country.trim().isEmpty() && !"CN".equals(country)) {
            fullAddress.append(country).append(" ");
        }
        
        if (state != null && !state.trim().isEmpty()) {
            fullAddress.append(state);
        }
        
        if (city != null && !city.trim().isEmpty()) {
            fullAddress.append(city);
        }
        
        if (district != null && !district.trim().isEmpty()) {
            fullAddress.append(district);
        }
        
        if (street != null && !street.trim().isEmpty()) {
            fullAddress.append(street);
        }
        
        if (detail != null && !detail.trim().isEmpty()) {
            fullAddress.append(detail);
        }
        
        String result = fullAddress.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 获取简短地址
     * 
     * 返回城市级别的地址信息
     * 
     * @return 简短地址
     */
    public String getShortAddress() {
        StringBuilder shortAddress = new StringBuilder();
        
        if (state != null && !state.trim().isEmpty()) {
            shortAddress.append(state);
        }
        
        if (city != null && !city.trim().isEmpty()) {
            if (shortAddress.length() > 0) {
                shortAddress.append(" ");
            }
            shortAddress.append(city);
        }
        
        String result = shortAddress.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 检查地址信息是否完整
     * 
     * @return 地址信息是否完整
     */
    public boolean isComplete() {
        return (state != null && !state.trim().isEmpty()) &&
               (city != null && !city.trim().isEmpty()) &&
               (street != null && !street.trim().isEmpty());
    }

    /**
     * 检查是否有GPS坐标
     * 
     * @return 是否有GPS坐标
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * 检查是否为中国地址
     * 
     * @return 是否为中国地址
     */
    public boolean isChinaAddress() {
        return country == null || "CN".equalsIgnoreCase(country) || 
               "CHN".equalsIgnoreCase(country) || "中国".equals(country);
    }

    /**
     * 获取地区代码
     * 
     * 用于税务和行政区划识别
     * 
     * @return 地区代码
     */
    public String getRegionCode() {
        if (!isChinaAddress()) {
            return country;
        }
        
        // 简单的省份代码映射
        if (state != null) {
            if (state.contains("广东")) return "GD";
            if (state.contains("上海")) return "SH";
            if (state.contains("北京")) return "BJ";
            if (state.contains("深圳")) return "SZ";
            if (state.contains("浙江")) return "ZJ";
            if (state.contains("江苏")) return "JS";
        }
        
        return "CN";
    }
}