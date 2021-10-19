/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package xyz.jaiiye.video.m3u8.spider;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 *
 * @author jaiiye
 */
public class VideoInfo implements Serializable {

    private String videoId;
    private String videoTitle;
    private List<String> actors;
    private String videoUrl;
    private String cover;
    private String thumb;
    private Date releaseDate;
    private Date uploadDate;
    private Integer videoDuration;
    private String videoNumber;
    private List<String> videoCategory;
    private List<String> videoTags;
    private String videoDescription;

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
    }

    public List<String> getActors() {
        return actors;
    }

    public void setActors(List<String> actors) {
        this.actors = actors;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getThumb() {
        return thumb;
    }

    public void setThumb(String thumb) {
        this.thumb = thumb;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Date getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(Date uploadDate) {
        this.uploadDate = uploadDate;
    }

    public Integer getVideoDuration() {
        return videoDuration;
    }

    public void setVideoDuration(Integer videoDuration) {
        this.videoDuration = videoDuration;
    }

    public String getVideoNumber() {
        return videoNumber;
    }

    public void setVideoNumber(String videoNumber) {
        this.videoNumber = videoNumber;
    }

    public List<String> getVideoCategory() {
        return videoCategory;
    }

    public void setVideoCategory(List<String> videoCategory) {
        this.videoCategory = videoCategory;
    }

    public List<String> getVideoTags() {
        return videoTags;
    }

    public void setVideoTags(List<String> videoTags) {
        this.videoTags = videoTags;
    }

    public String getVideoDescription() {
        return videoDescription;
    }

    public void setVideoDescription(String videoDescription) {
        this.videoDescription = videoDescription;
    }

}
