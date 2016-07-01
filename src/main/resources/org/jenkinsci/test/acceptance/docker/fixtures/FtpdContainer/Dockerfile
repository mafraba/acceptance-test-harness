FROM stilliard/pure-ftpd

# create a test user
RUN useradd test -d /home/test -s /bin/bash && \
        mkdir -p /home/test/tmp && \
        chown -R test /home/test && \
        echo "test:test" | chpasswd

# copy system users to FTP users file
RUN pure-pwconvert >> /etc/pure-ftpd/pureftpd.passwd
RUN pure-pw mkdb