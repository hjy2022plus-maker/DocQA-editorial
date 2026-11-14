# 预装 IK 中文分词插件，避免每次启动都重新下载
FROM elasticsearch:8.10.4

RUN bin/elasticsearch-plugin install --batch \
    https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-8.10.4.zip
