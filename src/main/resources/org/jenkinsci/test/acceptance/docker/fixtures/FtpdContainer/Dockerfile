FROM stilliard/pure-ftpd

ENV PUBLICHOST localhost

# create a test user
RUN useradd test -d /home/test -s /bin/bash && \
        mkdir -p /home/test && \
        chown -R test /home/test && \
        echo "test:test" | chpasswd

RUN (echo "test"; echo "test") | pure-pw useradd test -u test -D /home/test
RUN pure-pw mkdb
