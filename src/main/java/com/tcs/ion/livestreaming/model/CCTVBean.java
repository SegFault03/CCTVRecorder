package com.tcs.ion.livestreaming.model;

import java.util.Objects;

/**
 * POJO class representing a camera configuration from config.json
 */
public class CCTVBean {
    private int id;
    private String name;
    private String rtspUrl;
    private int chunkSize;
    private String startTime;
    private String endTime;

    // Default constructor for Gson
    public CCTVBean() {
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRtspUrl() {
        return rtspUrl;
    }

    public void setRtspUrl(String rtspUrl) {
        this.rtspUrl = rtspUrl;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "Camera{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", rtspUrl='" + rtspUrl + '\'' +
                ", chunkSize=" + chunkSize +
                ", startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CCTVBean CCTVBean = (CCTVBean) o;
        return chunkSize == CCTVBean.chunkSize &&
                Objects.equals(id, CCTVBean.id) &&
                Objects.equals(name, CCTVBean.name) &&
                Objects.equals(rtspUrl, CCTVBean.rtspUrl) &&
                Objects.equals(startTime, CCTVBean.startTime) &&
                Objects.equals(endTime, CCTVBean.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, rtspUrl, chunkSize, startTime, endTime);
    }
}